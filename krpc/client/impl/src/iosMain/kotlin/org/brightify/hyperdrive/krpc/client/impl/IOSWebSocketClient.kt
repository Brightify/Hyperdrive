package org.brightify.hyperdrive.krpc.client.impl

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
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
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionWebSocketCloseCode
import platform.Foundation.NSURLSessionWebSocketDelegateProtocol
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketTask
import platform.Foundation.create
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.native.concurrent.freeze

class IOSWebSocketClient(
    private val endpointUrl: NSURL
): RPCClientConnector {
    override suspend fun withConnection(block: suspend RPCConnection.() -> Unit) {
        val connectionJob = SupervisorJob()
        withContext(connectionJob) {
            val didOpen = CompletableDeferred<Unit>()
            val urlSession = NSURLSession.sessionWithConfiguration(
                configuration = NSURLSessionConfiguration.defaultSessionConfiguration(),
                delegate = object: NSObject(), NSURLSessionWebSocketDelegateProtocol {
                    override fun URLSession(session: NSURLSession, webSocketTask: NSURLSessionWebSocketTask, didOpenWithProtocol: String?) {
                        didOpen.complete(Unit)
                    }

                    override fun URLSession(
                        session: NSURLSession,
                        webSocketTask: NSURLSessionWebSocketTask,
                        didCloseWithCode: NSURLSessionWebSocketCloseCode,
                        reason: NSData?
                    ) {
                        connectionJob.complete()
                    }

                    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
                        if (didCompleteWithError != null) {
                            connectionJob.completeExceptionally(NSErrorThrowable(didCompleteWithError))
                        } else {
                            connectionJob.complete()
                        }
                    }
                },
                delegateQueue = NSOperationQueue.currentQueue
            )

            val task = urlSession.webSocketTaskWithURL(endpointUrl)

            val connection = Connection(task, this)

            task.resume()
            didOpen.await()

            try {
                block(connection)
            } finally {
                task.cancel()
            }
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
            this.ensureActive()
            val result = CompletableDeferred<SerializedFrame>()

            websocket.receiveMessageWithCompletionHandler { webSocketMessage, error ->
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
                    result.completeExceptionally(ConnectionClosedException("Received a socket error: ${error.localizedDescription}."))
                } else {
                    error("No result or error received from iOS websocket!")
                }
            }

            return result.await()
        }

        override suspend fun send(frame: SerializedFrame) {
            val result = CompletableDeferred<Unit>()

            val message = when (frame) {
                is SerializedFrame.Binary -> NSURLSessionWebSocketMessage(data = frame.binary.toNSData().freeze())
                is SerializedFrame.Text -> NSURLSessionWebSocketMessage(string = frame.text.freeze())
            }.freeze()
            websocket.sendMessage(message) { error ->
                if (error != null) {
                    result.completeExceptionally(NSErrorThrowable(error))
                } else {
                    result.complete(Unit)
                }
            }
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
}

