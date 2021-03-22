package org.brightify.hyperdrive.client.impl

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.serialization.modules.plus
import kotlinx.serialization.protobuf.ProtoBuf
import org.brightify.hyperdrive.krpc.api.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.api.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.api.RPCClientConnector
import org.brightify.hyperdrive.krpc.api.RPCConnection
import org.brightify.hyperdrive.krpc.api.RPCEvent
import org.brightify.hyperdrive.krpc.api.RPCFrameDeserializationStrategy
import org.brightify.hyperdrive.krpc.api.RPCFrameSerializationStrategy
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

private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).apply {
    usePinned {
        memcpy(it.addressOf(0), bytes, length)
    }
}
private fun ByteArray.toNSData(): NSData {
    val pinned = this.pin()
    return NSData.create(bytesNoCopy = pinned.addressOf(0), length = size.toULong()) { _, _ ->
        pinned.unpin()
    }
}

class IOSWebSocketClient(
    private val endpointUrl: NSURL
): RPCClientConnector {
    private val binaryFormat = ProtoBuf {
        encodeDefaults = false
        serializersModule += RPCEvent.serializersModule
    }

    private val deserializationStrategy = RPCFrameDeserializationStrategy<RPCEvent>()
    private val serializationStrategy = RPCFrameSerializationStrategy<RPCEvent>()

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

    inner class Connection(val websocket: NSURLSessionWebSocketTask, val scope: CoroutineScope): RPCConnection, CoroutineScope by scope {
        override suspend fun close() {
            websocket.cancel()
            scope.coroutineContext[Job]?.cancel()
        }

        override suspend fun receive(): IncomingRPCFrame<RPCEvent> {
            val result = CompletableDeferred<IncomingRPCFrame<RPCEvent>>()
            websocket.receiveMessageWithCompletionHandler { webSocketMessage, error ->
                if (webSocketMessage != null) {
                    val data = webSocketMessage.data
                    if (data != null) {
                        try {
                            val byteArray = data.toByteArray()
                            val frame = binaryFormat.decodeFromByteArray(deserializationStrategy, byteArray)
                            result.complete(frame)
                        } catch (t: Throwable) {
                            result.completeExceptionally(t)
                        }
                    } else {
                        result.completeExceptionally(RuntimeException("Non-data frames not supported!"))
                    }
                } else if (error != null) {
                    result.completeExceptionally(NSErrorThrowable(error))
                } else {
                    error("No result or error received from iOS websocket!")
                }
            }
            return result.await()
        }

        override suspend fun send(frame: OutgoingRPCFrame<RPCEvent>) {
            val result = CompletableDeferred<Unit>()
            val data = binaryFormat.encodeToByteArray(serializationStrategy, frame)

            val message = NSURLSessionWebSocketMessage(data.toNSData())
            websocket.sendMessage(message) { error ->
                if (error != null) {
                    result.completeExceptionally(NSErrorThrowable(error))
                } else {
                    result.complete(Unit)
                }
            }
            return result.await()
        }
    }
}

class NSErrorThrowable(val error: NSError): Throwable(error.description)
