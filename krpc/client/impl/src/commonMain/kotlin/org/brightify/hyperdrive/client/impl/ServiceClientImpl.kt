package org.brightify.hyperdrive.client.impl

import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.http.cio.websocket.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.brightify.hyperdrive.client.api.ServiceClient
import org.brightify.hyperdrive.krpc.api.*
import kotlin.reflect.KClass
import kotlin.time.Duration

class SingleFrameConverterWrapper private constructor(
    private val delegatedUpstreamFrameToWebSocketFrame: (RPCFrame<RPCEvent.Upstream>) -> Frame,
    private val delegatedDownstreamFrameToWebSocketFrame: (RPCFrame<RPCEvent.Downstream>) -> Frame,
    private val delegatedUpstreamFrameFromWebSocketFrame: (Frame, resolveCall: (RPCFrame.Header<RPCEvent.Upstream>) -> CallDescriptor) -> RPCFrame<RPCEvent.Upstream>,
    private val delegatedDownstreamFrameFromWebSocketFrame: (Frame, resolveCall: (RPCFrame.Header<RPCEvent.Downstream>) -> CallDescriptor) -> RPCFrame<RPCEvent.Downstream>
): WebSocketFrameConverter<Frame> {

    override fun upstreamFrameToWebSocketFrame(frame: RPCFrame<RPCEvent.Upstream>): Frame {
        return delegatedUpstreamFrameToWebSocketFrame(frame)
    }

    override fun downstreamFrameToWebSocketFrame(frame: RPCFrame<RPCEvent.Downstream>): Frame {
        return delegatedDownstreamFrameToWebSocketFrame(frame)
    }

    override fun upstreamFrameFromWebSocketFrame(frame: Frame, resolveCall: (RPCFrame.Header<RPCEvent.Upstream>) -> CallDescriptor): RPCFrame<RPCEvent.Upstream> {
        return delegatedUpstreamFrameFromWebSocketFrame(frame, resolveCall)
    }

    override fun downstreamFrameFromWebSocketFrame(frame: Frame, resolveCall: (RPCFrame.Header<RPCEvent.Downstream>) -> CallDescriptor): RPCFrame<RPCEvent.Downstream> {
        return delegatedDownstreamFrameFromWebSocketFrame(frame, resolveCall)
    }

    companion object {
        fun binary(binaryConverter: WebSocketFrameConverter<Frame.Binary>): SingleFrameConverterWrapper {
            return SingleFrameConverterWrapper(
                delegatedUpstreamFrameToWebSocketFrame = binaryConverter::upstreamFrameToWebSocketFrame,
                delegatedDownstreamFrameToWebSocketFrame = binaryConverter::downstreamFrameToWebSocketFrame,
                delegatedUpstreamFrameFromWebSocketFrame = { frame, resolveCall ->
                    when (frame) {
                        is Frame.Binary -> binaryConverter.upstreamFrameFromWebSocketFrame(frame, resolveCall)
                        else -> throw WebSocketFrameConverter.UnsupportedFrameTypeException(frame.frameType, binaryConverter)
                    }
                },
                delegatedDownstreamFrameFromWebSocketFrame = { frame, resolveCall ->
                    when (frame) {
                        is Frame.Binary -> binaryConverter.downstreamFrameFromWebSocketFrame(frame, resolveCall)
                        else -> throw WebSocketFrameConverter.UnsupportedFrameTypeException(frame.frameType, binaryConverter)
                    }
                }
            )
        }

        fun text(textConverter: WebSocketFrameConverter<Frame.Text>): SingleFrameConverterWrapper {
            return SingleFrameConverterWrapper(
                delegatedUpstreamFrameToWebSocketFrame = textConverter::upstreamFrameToWebSocketFrame,
                delegatedDownstreamFrameToWebSocketFrame = textConverter::downstreamFrameToWebSocketFrame,
                delegatedUpstreamFrameFromWebSocketFrame = { frame, resolveCall ->
                    when (frame) {
                        is Frame.Text -> textConverter.upstreamFrameFromWebSocketFrame(frame, resolveCall)
                        else -> throw WebSocketFrameConverter.UnsupportedFrameTypeException(frame.frameType, textConverter)
                    }
                },
                delegatedDownstreamFrameFromWebSocketFrame = { frame, resolveCall ->
                    when (frame) {
                        is Frame.Text -> textConverter.downstreamFrameFromWebSocketFrame(frame, resolveCall)
                        else -> throw WebSocketFrameConverter.UnsupportedFrameTypeException(frame.frameType, textConverter)
                    }
                }
            )
        }
    }
}

class JSONWebSocketFrameConverter(
    private val upstreamFrameSerializer: RPCFrameSerializer<RPCEvent.Upstream>,
    private val downstreamFrameSerializer: RPCFrameSerializer<RPCEvent.Downstream>
): WebSocketFrameConverter<Frame.Text> {
    override fun upstreamFrameToWebSocketFrame(frame: RPCFrame<RPCEvent.Upstream>): Frame.Text {
        return Frame.Text(Json.encodeToString(upstreamFrameSerializer, frame))
    }

    override fun downstreamFrameToWebSocketFrame(frame: RPCFrame<RPCEvent.Downstream>): Frame.Text {
        return Frame.Text(Json.encodeToString(downstreamFrameSerializer, frame))
    }

    override fun upstreamFrameFromWebSocketFrame(frame: Frame.Text, resolveCall: (RPCFrame.Header<RPCEvent.Upstream>) -> CallDescriptor): RPCFrame<RPCEvent.Upstream> {
        return Json.decodeFromString(upstreamFrameSerializer, frame.readText())
    }

    override fun downstreamFrameFromWebSocketFrame(frame: Frame.Text, resolveCall: (RPCFrame.Header<RPCEvent.Downstream>) -> CallDescriptor): RPCFrame<RPCEvent.Downstream> {
        return Json.decodeFromString(downstreamFrameSerializer, frame.readText())
    }
}

class ProtoBufWebSocketFrameConverter(
    private val upstreamFrameSerializer: RPCFrameSerializer<RPCEvent.Upstream>,
    private val downstreamFrameSerializer: RPCFrameSerializer<RPCEvent.Downstream>
): WebSocketFrameConverter<Frame.Binary> {
    override fun upstreamFrameToWebSocketFrame(frame: RPCFrame<RPCEvent.Upstream>): Frame.Binary {
        return Frame.Binary(true, ProtoBuf.encodeToByteArray(upstreamFrameSerializer, frame))
    }

    override fun downstreamFrameToWebSocketFrame(frame: RPCFrame<RPCEvent.Downstream>): Frame.Binary {
        return Frame.Binary(true, ProtoBuf.encodeToByteArray(downstreamFrameSerializer, frame))
    }

    override fun upstreamFrameFromWebSocketFrame(frame: Frame.Binary, resolveCall: (RPCFrame.Header<RPCEvent.Upstream>) -> CallDescriptor): RPCFrame<RPCEvent.Upstream> {
        return ProtoBuf.decodeFromByteArray(upstreamFrameSerializer, frame.readBytes())
    }

    override fun downstreamFrameFromWebSocketFrame(frame: Frame.Binary, resolveCall: (RPCFrame.Header<RPCEvent.Downstream>) -> CallDescriptor): RPCFrame<RPCEvent.Downstream> {
        return ProtoBuf.decodeFromByteArray(downstreamFrameSerializer, frame.readBytes())
    }
}

class WebSocketClient(
    private val connectionScope: CoroutineScope,
    private val host: String = "localhost",
    private val port: Int = 8000,
    private val frameConverter: WebSocketFrameConverter<Frame>
): RPCTransport<RPCEvent.Downstream, RPCEvent.Upstream> {
    private val httpClient = HttpClient {
        install(WebSockets)
    }

    private var ws: ClientWebSocketSession? = null
    private var connectingMutex = Mutex()

    init {
        connectionScope.launch {
//            while (isActive) {
                connect()
//            }
        }
    }

    override suspend fun send(frame: RPCFrame<RPCEvent.Upstream>) {
        val session = connect()

        session.send(frameConverter.upstreamFrameToWebSocketFrame(frame))
    }

    override suspend fun receive(resolveCall: (RPCFrame.Header<RPCEvent.Downstream>) -> CallDescriptor): RPCFrame<RPCEvent.Downstream> {
        val session = connect()
        val frame = session.incoming.receive()

        return frameConverter.downstreamFrameFromWebSocketFrame(frame, resolveCall)
    }

    private suspend fun connect(): ClientWebSocketSession {
        return connectingMutex.withLock {
            val oldSession = ws
            if (oldSession != null) {
                return@withLock oldSession
            }
            val session = httpClient.webSocketSession(host = host, port = port)
            ws = session
            return@withLock session
        }
    }
}

class ServiceClientImpl(
    private val transport: RPCTransport<RPCEvent.Downstream, RPCEvent.Upstream>,
    private val communicationScope: CoroutineScope
): ServiceClient {

    private var frameIndexCounter: RPCFrameIndex = RPCFrameIndex.MIN_VALUE
    private val pendingRequests: MutableMap<Int, CallDescriptor> = mutableMapOf()
    private val runJob: Job

    init {
        runJob = communicationScope.launch {
            while (isActive) {
                try {
                    val frame = transport.receive { header ->
                        when (val event = header.event) {
                            is RPCEvent.Downstream.SingleCall.Response -> {
                                val pendingRequest = pendingRequests.getOrElse(event.requestIndex) {
                                    TODO()
                                }

                                pendingRequest
                            }
                            is RPCEvent.Downstream.SingleCall.Error -> TODO()
                            is RPCEvent.Downstream.ClientStream.Response -> TODO()
                            is RPCEvent.Downstream.ClientStream.Error -> TODO()
                            is RPCEvent.Downstream.ServerStream.SendDownstream -> TODO()
                            is RPCEvent.Downstream.ServerStream.Close -> TODO()
                            is RPCEvent.Downstream.BidiStream.SendDownstream -> TODO()
                            is RPCEvent.Downstream.BidiStream.Close -> TODO()
                        }
                    }

                    when (val event = frame.header.event) {
                        is RPCEvent.Downstream.SingleCall.Response -> {
                            val deferred = pendingRequests.getOrElse(event.requestIndex) {
                                TODO()
                            }

                            deferred.second.complete(frame.data)
                        }
                        is RPCEvent.Downstream.SingleCall.Error -> {
                            val deferred = pendingRequests.getOrElse(frame.header.index) {
                                TODO()
                            }

                            deferred.second.completeExceptionally(frame.data as Throwable)
                        }
                        is RPCEvent.Downstream.ClientStream.Response<*> -> TODO()
                        is RPCEvent.Downstream.ServerStream.SendDownstream -> TODO()
                        is RPCEvent.Downstream.ServerStream.Close -> TODO()
                        is RPCEvent.Downstream.BidiStream.SendDownstream -> TODO()
                        is RPCEvent.Downstream.BidiStream.Close -> TODO()
                    }
                } catch (e: ClosedReceiveChannelException) {
                    // Disconnected from server, wait and then try again
                    delay(500)
                } catch (e: EOFException) {
                    // Disconnected from server, wait and then try again
                    delay(500)
                }
            }
        }
    }

    override suspend fun <REQUEST, RESPONSE> singleCall(serviceCall: ClientCallDescriptor<REQUEST, RESPONSE>, request: REQUEST): RESPONSE {
        val event = RPCEvent.Upstream.SingleCall.Request(serviceCall.identifier)
        val deferred = CompletableDeferred<RESPONSE>()
        val frame = RPCFrame(
            header = RPCFrame.Header(
                index = nextFrameIndex(),
                event = event
            ),
            data = request,
            dataSerializer = serviceCall.outgoingSerializer
        )

        pendingRequests[frame.header.index] = serviceCall.with(deferred)

        transport.send(frame)

        return deferred.await()
    }

    override suspend fun <REQUEST, RESPONSE> clientStream(descriptor: CallDescriptor, request: Flow<REQUEST>): RESPONSE {
        TODO("Not yet implemented")
    }

    override suspend fun <REQUEST, RESPONSE> serverStream(descriptor: CallDescriptor, request: REQUEST): Flow<RESPONSE> {
        TODO("Not yet implemented")
    }

    override suspend fun <REQUEST, RESPONSE> bidiStream(descriptor: CallDescriptor, request: Flow<REQUEST>): Flow<RESPONSE> {
        TODO("Not yet implemented")
    }

    override fun shutdown() {
        runJob.cancel()
    }

    private fun nextFrameIndex(): Int {
        return frameIndexCounter++
    }
}
