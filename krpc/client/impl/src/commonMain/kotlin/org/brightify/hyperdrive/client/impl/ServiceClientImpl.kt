package org.brightify.hyperdrive.client.impl

import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.http.cio.websocket.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import org.brightify.hyperdrive.client.api.ServiceClient
import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.ClientCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdBistreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdDownstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdUpstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.api.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.api.RPCEvent
import org.brightify.hyperdrive.krpc.api.RPCFrame
import org.brightify.hyperdrive.krpc.api.RPCFrameDeserializationStrategy
import org.brightify.hyperdrive.krpc.api.RPCFrameSerializationStrategy
import org.brightify.hyperdrive.krpc.api.RPCReference
import org.brightify.hyperdrive.krpc.api.RPCTransport
import org.brightify.hyperdrive.krpc.api.WebSocketFrameConverter
import org.brightify.hyperdrive.krpc.api.error.NonRPCErrorThrownError
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer
import kotlin.collections.MutableMap
import kotlin.collections.getOrElse
import kotlin.collections.mutableMapOf
import kotlin.collections.set

class SingleFrameConverterWrapper<OUTGOING: RPCEvent, INCOMING: RPCEvent> private constructor(
    private val delegatedRpcFrameToWebSocketFrame: (OutgoingRPCFrame<OUTGOING>) -> Frame,
    private val delegatedRpcFrameFromWebSocketFrame: (Frame) -> IncomingRPCFrame<INCOMING>
): WebSocketFrameConverter<Frame, OUTGOING, INCOMING> {

    override fun rpcFrameToWebSocketFrame(frame: OutgoingRPCFrame<OUTGOING>): Frame = delegatedRpcFrameToWebSocketFrame(frame)

    override fun rpcFrameFromWebSocketFrame(frame: Frame): IncomingRPCFrame<INCOMING> = delegatedRpcFrameFromWebSocketFrame(frame)

    companion object {
        fun <OUTGOING: RPCEvent, INCOMING: RPCEvent> binary(binaryConverter: WebSocketFrameConverter<Frame.Binary, OUTGOING, INCOMING>): SingleFrameConverterWrapper<OUTGOING, INCOMING> {
            return SingleFrameConverterWrapper(
                delegatedRpcFrameToWebSocketFrame = binaryConverter::rpcFrameToWebSocketFrame,
                delegatedRpcFrameFromWebSocketFrame = { frame ->
                    when (frame) {
                        is Frame.Binary -> binaryConverter.rpcFrameFromWebSocketFrame(frame)
                        else -> throw WebSocketFrameConverter.UnsupportedFrameTypeException(frame.frameType, binaryConverter)
                    }
                }
            )
        }

        fun <OUTGOING: RPCEvent, INCOMING: RPCEvent> text(textConverter: WebSocketFrameConverter<Frame.Text, OUTGOING, INCOMING>): SingleFrameConverterWrapper<OUTGOING, INCOMING> {
            return SingleFrameConverterWrapper(
                delegatedRpcFrameToWebSocketFrame = textConverter::rpcFrameToWebSocketFrame,
                delegatedRpcFrameFromWebSocketFrame = { frame ->
                    when (frame) {
                        is Frame.Text -> textConverter.rpcFrameFromWebSocketFrame(frame)
                        else -> throw WebSocketFrameConverter.UnsupportedFrameTypeException(frame.frameType, textConverter)
                    }
                }
            )
        }
    }
}

class JSONWebSocketFrameConverter<OUTGOING: RPCEvent, INCOMING: RPCEvent>(
    private val outgoingSerializer: RPCFrameSerializationStrategy<OUTGOING>,
    private val incomingDeserializer: RPCFrameDeserializationStrategy<INCOMING>
): WebSocketFrameConverter<Frame.Text, OUTGOING, INCOMING> {

    override fun rpcFrameToWebSocketFrame(frame: OutgoingRPCFrame<OUTGOING>): Frame.Text {
        return Frame.Text(Json.encodeToString(outgoingSerializer, frame))
    }

    override fun rpcFrameFromWebSocketFrame(frame: Frame.Text): IncomingRPCFrame<INCOMING> {
        return Json.decodeFromString(incomingDeserializer, frame.readText())
    }

}

class ProtoBufWebSocketFrameConverter<OUTGOING: RPCEvent, INCOMING: RPCEvent>(
    private val outgoingSerializer: RPCFrameSerializationStrategy<OUTGOING>,
    private val incomingDeserializer: RPCFrameDeserializationStrategy<INCOMING>
): WebSocketFrameConverter<Frame.Binary, OUTGOING, INCOMING> {

    override fun rpcFrameToWebSocketFrame(frame: OutgoingRPCFrame<OUTGOING>): Frame.Binary {
        return Frame.Binary(true, ProtoBuf.encodeToByteArray(outgoingSerializer, frame))
    }

    override fun rpcFrameFromWebSocketFrame(frame: Frame.Binary): IncomingRPCFrame<INCOMING> {
        return ProtoBuf.decodeFromByteArray(incomingDeserializer, frame.readBytes())
    }

}

class WebSocketClient(
    private val connectionScope: CoroutineScope,
    private val host: String = "localhost",
    private val port: Int = 8000,
    private val frameConverter: WebSocketFrameConverter<Frame, RPCEvent.Upstream, RPCEvent.Downstream>
): RPCTransport<RPCEvent.Upstream, RPCEvent.Downstream> {
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

    override suspend fun send(frame: OutgoingRPCFrame<RPCEvent.Upstream>) {
        val session = connect()

        session.send(frameConverter.rpcFrameToWebSocketFrame(frame))
    }

    override suspend fun receive(): IncomingRPCFrame<RPCEvent.Downstream> {
        val session = connect()
        val frame = session.incoming.receive()

        return frameConverter.rpcFrameFromWebSocketFrame(frame)
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

interface PendingRPC<INCOMING> {
    val stateManager: StateManager

    val deserializationStrategy: DeserializationStrategy<INCOMING>

    val errorSerializer: RPCErrorSerializer

    suspend fun accept(data: INCOMING)

    suspend fun reject(throwable: Throwable)

    suspend fun close(throwable: Throwable?)

    enum class State {
        Closed,
        Busy,
        Ready,
    }

    class StateManager {
        var state = State.Closed
            private set

        private val observers: MutableMap<State, MutableSet<CompletableDeferred<Unit>>> = mutableMapOf()

        fun setOpened() {
            require(state == State.Closed) { "Required to be closed, was $state" }

            state = State.Ready

            notifyStateChanged()
        }

        fun setBusy() {
            require(state == State.Ready)

            state = State.Busy

            notifyStateChanged()
        }

        fun setReady() {
            require(state == State.Busy)

            state = State.Ready

            notifyStateChanged()
        }

        suspend fun await(state: State) {
            if (this.state == state) {
                return
            } else {
                val observer = CompletableDeferred<Unit>()
                observers.getOrPut(state, ::mutableSetOf).add(observer)
                observer.await()
            }
        }

        private fun notifyStateChanged() {
            val state = this.state
            val stateObservers = observers.remove(state) ?: return

            for (observer in stateObservers) {
                observer.complete(Unit)
            }
        }
    }
}

abstract class PendingSingleCall<INCOMING>(
    override val deserializationStrategy: DeserializationStrategy<INCOMING>,
    override val errorSerializer: RPCErrorSerializer,
): PendingRPC<INCOMING> {
    override val stateManager = PendingRPC.StateManager()
}

abstract class OpenOutStream<OUTGOING, INCOMING>(
    val serializationStrategy: SerializationStrategy<OUTGOING>,
    override val deserializationStrategy: DeserializationStrategy<INCOMING>,
    override val errorSerializer: RPCErrorSerializer,
    val stream: Flow<OUTGOING>,
): PendingRPC<INCOMING> {
    override val stateManager = PendingRPC.StateManager()
}

abstract class OpenInStream<INCOMING>(
    override val deserializationStrategy: DeserializationStrategy<INCOMING>,
    override val errorSerializer: RPCErrorSerializer,
): PendingRPC<INCOMING> {
    override val stateManager = PendingRPC.StateManager()
}

abstract class OpenBiStream<OUTGOING, INCOMING>(
    val serializationStrategy: SerializationStrategy<OUTGOING>,
    override val deserializationStrategy: DeserializationStrategy<INCOMING>,
    override val errorSerializer: RPCErrorSerializer,
    val stream: Flow<OUTGOING>,
): PendingRPC<INCOMING> {
    override val stateManager = PendingRPC.StateManager()
}

class ServiceClientImpl(
    private val transport: RPCTransport<RPCEvent.Upstream, RPCEvent.Downstream>,
    private val communicationScope: CoroutineScope,
    private val outStreamScope: CoroutineScope
): ServiceClient {

    private var callReferenceCounter: RPCReference = RPCReference.MIN_VALUE
    private val pendingRequests: MutableMap<Int, PendingRPC<Any>> = mutableMapOf()
    private val runJob: Job

    init {
        runJob = communicationScope.launch {
            while (isActive) {
                try {
                    val frame = transport.receive()
                    println("Client - $frame")
                    val exhaustive = when (val event = frame.header.event) {
                        is RPCEvent.Downstream.Opened -> {
                            // TODO this means the server accepted the call, we can start sending data now if we have a stream
                            val pendingCall = pendingRequests.getOrElse(frame.header.callReference) {
                                TODO()
                            }

                            pendingCall.stateManager.setOpened()
                        }
                        is RPCEvent.Downstream.Response,
                        is RPCEvent.Downstream.Data -> {
                            val pendingCall = pendingRequests.getOrElse(frame.header.callReference) {
                                TODO()
                            }

                            val data = frame.decoder.decodeSerializableValue(pendingCall.deserializationStrategy)
                            pendingCall.accept(data)
                        }
                        is RPCEvent.Downstream.Error, is RPCEvent.Downstream.Warning -> {
                            val pendingCall = pendingRequests.getOrElse(frame.header.callReference) {
                                TODO()
                            }

                            val throwable = frame.decoder.decodeSerializableValue(pendingCall.errorSerializer)
                            pendingCall.reject(throwable)
                        }
                        is RPCEvent.Downstream.Close -> {
                            val pendingCall = pendingRequests.getOrElse(frame.header.callReference) {
                                TODO()
                            }

                            // TODO: Figure out how to send errors
//                            val throwable = frame.decoder.decodeNullableSerializableValue(serializer<Throwable?>())
                            pendingCall.close(null) // throwable)
                        }
                    }
                } catch (e: ClosedReceiveChannelException) {
                    // Disconnected from server, wait and then try again
                    delay(500)
                } catch (e: EOFException) {
                    // Disconnected from server, wait and then try again
                    delay(500)
                } catch (t: Throwable) {
                    t.printStackTrace()
                    throw t
                }
            }
        }
    }

    override suspend fun <REQUEST, RESPONSE> singleCall(serviceCall: ClientCallDescriptor<REQUEST, RESPONSE>, request: REQUEST): RESPONSE {
        val event = RPCEvent.Upstream.Open(serviceCall.identifier)
        val frame = OutgoingRPCFrame(
            header = RPCFrame.Header(
                callReference = nextCallReference(),
                event = event,
            ),
            serializationStrategy = serviceCall.outgoingSerializer as SerializationStrategy<Any?>,
            data = request,
        )

        val deferred = CompletableDeferred<RESPONSE>()
        val pendingCall = object: PendingSingleCall<RESPONSE>(serviceCall.incomingSerializer, serviceCall.errorSerializer) {
            override suspend fun accept(data: RESPONSE) {
                deferred.complete(data)
            }

            override suspend fun reject(throwable: Throwable) {
                deferred.completeExceptionally(throwable)
            }

            override suspend fun close(throwable: Throwable?) {
                deferred.cancel(CancellationException("Server closed the connection unexpectedly!", throwable))
            }
        }

        pendingRequests[frame.header.callReference] = pendingCall as PendingRPC<Any>

        transport.send(frame)

        return deferred.await()
    }

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> clientStream(serviceCall: ColdUpstreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>, request: REQUEST, clientStream: Flow<CLIENT_STREAM>): RESPONSE {
        val event = RPCEvent.Upstream.Open(serviceCall.identifier)
        val callReference = nextCallReference()
        val frame = OutgoingRPCFrame(
            header = RPCFrame.Header(
                callReference = callReference,
                event = event
            ),
            serializationStrategy = serviceCall.outgoingSerializer as SerializationStrategy<Any?>,
            data = request
        )

        var streamingJob: Job? = null
        val deferred = CompletableDeferred<RESPONSE>()
        val openStream = object: OpenOutStream<CLIENT_STREAM, RESPONSE>(
            serializationStrategy = serviceCall.clientStreamSerializer,
            deserializationStrategy = serviceCall.incomingSerializer,
            errorSerializer = serviceCall.errorSerializer,
            clientStream
        ) {
            override suspend fun accept(data: RESPONSE) {
                deferred.complete(data)
            }

            override suspend fun reject(throwable: Throwable) {
                deferred.completeExceptionally(throwable)
            }

            override suspend fun close(throwable: Throwable?) {
                val capturedStreamingJob = streamingJob
                if (capturedStreamingJob != null) {
                    capturedStreamingJob.cancel(CancellationException("Server closed the connection.", throwable))
                } else {
                    streamingJob = Job()
                }
            }
        }

        pendingRequests[frame.header.callReference] = openStream as PendingRPC<Any>

        transport.send(frame)
        openStream.stateManager.await(PendingRPC.State.Ready)

        if (streamingJob == null) {
            streamingJob = outStreamScope.launch {
                try {
                    clientStream.collect {
                        transport.send(
                            OutgoingRPCFrame(
                                header = RPCFrame.Header(
                                    callReference = callReference,
                                    event = RPCEvent.Upstream.Data
                                ),
                                serializationStrategy = serviceCall.clientStreamSerializer as SerializationStrategy<Any?>,
                                data = it
                            )
                        )
                    }

                    transport.send(
                        OutgoingRPCFrame(
                            header = RPCFrame.Header(
                                callReference = callReference,
                                event = RPCEvent.Upstream.Close
                            ),
                            serializationStrategy = Unit.serializer() as SerializationStrategy<Any?>,
                            data = Unit
                        )
                    )
                } catch (t: Throwable) {
                    transport.send(
                        OutgoingRPCFrame(
                            header = RPCFrame.Header(
                                callReference = callReference,
                                event = RPCEvent.Upstream.Error,
                            ),
                            serializationStrategy = serviceCall.errorSerializer as SerializationStrategy<Any?>,
                            data = t,
                        )
                    )
                }
            }
        } else {
            assert(false) { "" }
        }

        return deferred.await()
    }

    override suspend fun <REQUEST, RESPONSE> serverStream(
        serviceCall: ColdDownstreamCallDescriptor<REQUEST, RESPONSE>,
        request: REQUEST
    ): Flow<RESPONSE> = flow {
        val event = RPCEvent.Upstream.Open(serviceCall.identifier)
        val callReference = nextCallReference()
        val frame = OutgoingRPCFrame(
            header = RPCFrame.Header(
                callReference = callReference,
                event = event
            ),
            serializationStrategy = serviceCall.outgoingSerializer as SerializationStrategy<Any?>,
            data = request
        )

        val streamChannel = Channel<RESPONSE>()
        val openStream = object: OpenInStream<RESPONSE>(
            deserializationStrategy = serviceCall.serverStreamSerializer,
            errorSerializer = serviceCall.errorSerializer,
        ) {
            override suspend fun accept(data: RESPONSE) {
                streamChannel.send(data)
            }

            override suspend fun reject(throwable: Throwable) {
                streamChannel.close(throwable)
            }

            override suspend fun close(throwable: Throwable?) {
                streamChannel.close(throwable)
            }
        }

        pendingRequests[frame.header.callReference] = openStream as PendingRPC<Any>

        transport.send(frame)

        emitAll(streamChannel)
    }

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> biStream(
        serviceCall: ColdBistreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>,
        request: REQUEST,
        clientStream: Flow<CLIENT_STREAM>
    ): Flow<RESPONSE> = flow {
        val event = RPCEvent.Upstream.Open(serviceCall.identifier)
        val callReference = nextCallReference()
        val frame = OutgoingRPCFrame(
            header = RPCFrame.Header(
                callReference = callReference,
                event = event
            ),
            serializationStrategy = serviceCall.outgoingSerializer as SerializationStrategy<Any?>,
            data = request
        )

        var streamingJob: Job? = null
        val streamChannel = Channel<RESPONSE>()
        val openStream = object: OpenInStream<RESPONSE>(
            deserializationStrategy = serviceCall.serverStreamSerializer,
            errorSerializer = serviceCall.errorSerializer,
        ) {
            override suspend fun accept(data: RESPONSE) {
                streamChannel.send(data)
            }

            override suspend fun reject(throwable: Throwable) {
                streamChannel.close(throwable)
            }

            override suspend fun close(throwable: Throwable?) {
                val capturedStreamingJob = streamingJob
                if (capturedStreamingJob != null) {
                    capturedStreamingJob.cancel(CancellationException("Server closed the connection.", throwable))
                } else {
                    streamingJob = Job()
                }
                streamChannel.close(throwable)
            }
        }

        pendingRequests[frame.header.callReference] = openStream as PendingRPC<Any>

        transport.send(frame)

        openStream.stateManager.await(PendingRPC.State.Ready)

        if (streamingJob == null) {
            streamingJob = outStreamScope.launch {
                try {
                    clientStream.collect {
                        transport.send(
                            OutgoingRPCFrame(
                                header = RPCFrame.Header(
                                    callReference = callReference,
                                    event = RPCEvent.Upstream.Data
                                ),
                                serializationStrategy = serviceCall.clientStreamSerializer as SerializationStrategy<Any?>,
                                data = it
                            )
                        )
                    }

                    transport.send(
                        OutgoingRPCFrame(
                            header = RPCFrame.Header(
                                callReference = callReference,
                                event = RPCEvent.Upstream.Close
                            ),
                            serializationStrategy = Unit.serializer() as SerializationStrategy<Any?>,
                            data = Unit
                        )
                    )
                } catch (t: Throwable) {
                    transport.send(
                        OutgoingRPCFrame(
                            header = RPCFrame.Header(
                                callReference = callReference,
                                event = RPCEvent.Upstream.Error,
                            ),
                            serializationStrategy = serviceCall.errorSerializer as SerializationStrategy<Any?>,
                            data = t,
                        )
                    )
                }
            }
        } else {
            assert(false) { "" }
        }

        emitAll(streamChannel)
    }

    override fun shutdown() {
        runJob.cancel()
    }

    private fun nextCallReference(): Int {
        return callReferenceCounter++
    }
}
