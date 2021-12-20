package org.brightify.hyperdrive.krpc.client.impl

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.SerializedFrame
import org.brightify.hyperdrive.krpc.client.RPCClientConnector
import org.brightify.hyperdrive.krpc.error.ConnectionClosedException
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionWebSocketCloseCode
import platform.Foundation.NSURLSessionWebSocketDelegateProtocol
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketTask
import platform.Foundation.create
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.coroutineContext
import kotlin.native.concurrent.Worker
import kotlin.native.concurrent.freeze
import kotlin.native.concurrent.isFrozen

class DarwinWebSocketClient(
    private val endpointUrl: NSURL
): RPCClientConnector {
    var currentTask: NSURLSessionWebSocketTask? = null

    override suspend fun withConnection(block: suspend RPCConnection.() -> Unit): Unit = coroutineScope {
        val connectionJob = SupervisorJob()
        val didOpen = CompletableDeferred<Unit>(connectionJob)
        val didClose = CompletableDeferred<Unit>(connectionJob)
        val delegate = Delegate(didOpen, didClose) //.apply { freeze() }
        val urlSession = NSURLSession.sessionWithConfiguration(
            configuration = NSURLSessionConfiguration.defaultSessionConfiguration(),
            delegate = delegate,
            delegateQueue = NSOperationQueue.currentQueue
        )

        val task = urlSession.webSocketTaskWithURL(endpointUrl) //.apply { freeze() }
        currentTask = task

        val connection = Connection(task, this)

        task.resume()
        didOpen.await()

        try {
            listOf(
                connection.async { connection.block() },
                didClose,
            ).awaitAll()
        } finally {
            task.cancel()
        }
    }

    override fun isConnectionCloseException(throwable: Throwable): Boolean {
        return throwable is NSErrorThrowable
    }

    inner class Connection(val websocket: NSURLSessionWebSocketTask, val scope: CoroutineScope): RPCConnection, CoroutineScope by scope + CoroutineName("IOSWebSocketClient.Connection") {
        override suspend fun close() {
            websocket.cancel()
            scope.coroutineContext[Job]?.cancel()
        }

        override suspend fun receive(): SerializedFrame {
            scope.ensureActive()
            val result = CompletableDeferred<SerializedFrame>()

            val completionHandler: (NSURLSessionWebSocketMessage?, NSError?) -> Unit = { webSocketMessage, error ->
                if (webSocketMessage != null) {
                    val data = webSocketMessage.data
                    val string = webSocketMessage.string

                    when {
                        data != null -> {
                            try {
                                val byteArray = data.toByteArray()
                                result.complete(SerializedFrame.Binary(byteArray))
                            } catch (t: Throwable) {
                                result.completeExceptionally(t)
                            }
                        }
                        string != null -> {
                            result.complete(SerializedFrame.Text(string))
                        }
                        else -> {
                            result.completeExceptionally(RuntimeException("Neither data nor string received!"))
                        }
                    }
                } else if (error != null) {
                    result.completeExceptionally(ConnectionClosedException("Received a socket error: ${error.localizedDescription}.", NSErrorThrowable(error)))
                } else {
                    error("No result or error received from iOS websocket!")
                }
            }
            websocket.receiveMessageWithCompletionHandler(completionHandler)

            return result.await()
        }

        override suspend fun send(frame: SerializedFrame) {
            scope.ensureActive()
            val result = CompletableDeferred<Unit>()

            val message = when (frame) {
                is SerializedFrame.Binary -> NSURLSessionWebSocketMessage(data = frame.binary.toNSData().freeze())
                is SerializedFrame.Text -> NSURLSessionWebSocketMessage(string = frame.text.freeze())
            }.freeze()

            val completionHandler: (NSError?) -> Unit = { error ->
                if (error != null) {
                    result.completeExceptionally(ConnectionClosedException("Connection closed due to an error.", NSErrorThrowable(error)))
                } else {
                    result.complete(Unit)
                }
            }
            websocket.sendMessage(message, completionHandler = completionHandler)
            return result.await()
        }

        private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).apply {
            usePinned {
                memcpy(it.addressOf(0), bytes, length)
            }
        }

        private fun ByteArray.toNSData(): NSData = usePinned {
            NSData.create(bytes = it.addressOf(0), length = size.toULong().freeze())
        }
    }

    class Delegate(private val didOpen: CompletableDeferred<Unit>, private val didClose: CompletableDeferred<Unit>): NSObject(), NSURLSessionWebSocketDelegateProtocol {
        override fun URLSession(session: NSURLSession, webSocketTask: NSURLSessionWebSocketTask, didOpenWithProtocol: String?) {
            println("W3: ${Worker.current.id} - ${Worker.current.name}")
            println("did open")
            didOpen.complete(Unit)
        }

        override fun URLSession(
            session: NSURLSession,
            webSocketTask: NSURLSessionWebSocketTask,
            didCloseWithCode: NSURLSessionWebSocketCloseCode,
            reason: NSData?
        ) {
            println("W4: ${Worker.current.id} - ${Worker.current.name}")
            println("did close")
            didOpen.completeExceptionally(ConnectionClosedException())
            didClose.completeExceptionally(ConnectionClosedException())
        }

        override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
            println("W5: ${Worker.current.id} - ${Worker.current.name}")
            println("did error/complete: $didCompleteWithError")
            val exception = if (didCompleteWithError != null) {
                ConnectionClosedException("Connection closed because of an error.", NSErrorThrowable(didCompleteWithError))
            } else {
                ConnectionClosedException()
            }
            didOpen.completeExceptionally(exception)
            didClose.completeExceptionally(exception)
        }
    }
}
