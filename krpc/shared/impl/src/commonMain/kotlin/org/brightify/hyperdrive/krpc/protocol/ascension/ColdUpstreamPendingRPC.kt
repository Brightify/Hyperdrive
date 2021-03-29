package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Contextual
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.api.throwable
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.error.InternalServerError
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.frame.RPCFrame
import org.brightify.hyperdrive.krpc.util.RPCReference
import org.brightify.hyperdrive.krpc.error.RPCProtocolViolationError
import org.brightify.hyperdrive.krpc.frame.AscensionRPCFrame
import org.brightify.hyperdrive.krpc.protocol.RPC
import org.brightify.hyperdrive.krpc.protocol.RPCIncomingInterceptor
import org.brightify.hyperdrive.krpc.protocol.RPCOutgoingInterceptor
import org.brightify.hyperdrive.utils.Do
import kotlin.coroutines.cancellation.CancellationException

object ColdUpstreamPendingRPC {
    class Callee(
        protocol: AscensionRPCProtocol,
        scope: CoroutineScope,
        reference: RPCReference,
        private val implementation: RPC.Upstream.Callee.Implementation,
    ): PendingRPC.Callee<AscensionRPCFrame.ColdUpstream.Upstream, AscensionRPCFrame.ColdUpstream.Downstream>(protocol, scope, reference, logger) {
        private companion object {
            val logger = Logger<ColdUpstreamPendingRPC.Callee>()
        }

        private val channel = Channel<SerializedPayload>()

        override suspend fun handle(frame: AscensionRPCFrame.ColdUpstream.Upstream) {
            Do exhaustive when (frame) {
                is AscensionRPCFrame.ColdUpstream.Upstream.Open -> launch {
                    var streamConsumptionJob: CompletableJob? = null
                    val clientStreamFlow = channel.consumeAsFlow()
                        .onStart {
                            streamConsumptionJob = Job(coroutineContext.job)
                            startClientStream()
                        }
                        .onCompletion {
                            closeClientStream()
                            streamConsumptionJob?.complete()
                        }

                    val response = implementation.perform(frame.payload, clientStreamFlow)
                    send(AscensionRPCFrame.ColdUpstream.Downstream.Response(response, reference))
                }
                is AscensionRPCFrame.ColdUpstream.Upstream.StreamEvent -> {
                    channel.send(frame.event)
                }
            }
        }

        private suspend fun startClientStream() {
            send(AscensionRPCFrame.ColdUpstream.Downstream.StreamOperation.Start(reference))
        }

        private suspend fun closeClientStream() {
            send(AscensionRPCFrame.ColdUpstream.Downstream.StreamOperation.Close(reference))
        }
    }

    class Caller(
        protocol: AscensionRPCProtocol,
        scope: CoroutineScope,
        private val serviceCallIdentifier: ServiceCallIdentifier,
        reference: RPCReference,
    ): PendingRPC.Caller<AscensionRPCFrame.ColdUpstream.Downstream, AscensionRPCFrame.ColdUpstream.Upstream>(protocol, scope, reference, logger), RPC.Upstream.Caller {
        private companion object {
            val logger = Logger<ColdUpstreamPendingRPC.Caller>()
        }

        private val responseDeferred = CompletableDeferred<SerializedPayload>()
        private lateinit var stream: Flow<SerializedPayload>
        private lateinit var upstreamJob: Job

        override suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): SerializedPayload = withContext(this.coroutineContext) {
            this@Caller.stream = stream

            send(AscensionRPCFrame.ColdUpstream.Upstream.Open(payload, serviceCallIdentifier, reference))

            responseDeferred.await()
        }

        override suspend fun handle(frame: AscensionRPCFrame.ColdUpstream.Downstream) {
            Do exhaustive when (frame) {
                is AscensionRPCFrame.ColdUpstream.Downstream.StreamOperation.Start -> upstreamJob = launch {
                    if (!::stream.isInitialized) {
                        // This probably means we called `open` before setting the prepared stream, see `perform` method.
                        throw RPCProtocolViolationError("Upstream Client cannot start collecting before stream is prepared!.")
                    }

                    stream.collect {
                        send(AscensionRPCFrame.ColdUpstream.Upstream.StreamEvent(it, reference))
                    }
                }
                is AscensionRPCFrame.ColdUpstream.Downstream.StreamOperation.Close -> {
                    if (!this::upstreamJob.isInitialized) {
                        throw RPCProtocolViolationError("Upstream Client's stream was not started. Cannot close.")
                    }
                    upstreamJob.cancelAndJoin()
                }
                is AscensionRPCFrame.ColdUpstream.Downstream.Response -> {
                    responseDeferred.complete(frame.payload)
                }
            }
        }
    }
}

object ColdUpstreamRunner {
    class Callee<REQUEST, CLIENT_STREAM, RESPONSE>(
        val serializer: PayloadSerializer,
        val call: RunnableCallDescription.ColdUpstream<REQUEST, CLIENT_STREAM, RESPONSE>,
    ): RPC.Upstream.Callee.Implementation {
        private val streamEventSerializer = StreamEventSerializer(
            call.clientStreamSerializer,
            call.errorSerializer,
        )
        private val responseSerializer = ResponseSerializer(
            call.responseSerializer,
            call.errorSerializer,
        )

        override suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): SerializedPayload {
            val request = serializer.deserialize(call.requestSerializer, payload)

            val clientStream = stream
                .map {
                    return@map when (val event = serializer.deserialize(streamEventSerializer, it)) {
                        is StreamEvent.Element -> event.element
                        is StreamEvent.Complete -> throw CancellationException("Stream Completed")
                        is StreamEvent.Error -> throw event.error.throwable()
                    }
                }
                .catch { throwable ->
                    if (throwable !is CancellationException) {
                        throw throwable
                    }
                }

            return try {
                val response = call.perform(request, clientStream)
                serializer.serialize(responseSerializer, Response.Success(response))
            } catch (t: Throwable) {
                serializer.serialize(responseSerializer, Response.Error(t))
            }
        }
    }

    class Caller<REQUEST, CLIENT_STREAM, RESPONSE>(
        val serializer: PayloadSerializer,
        val rpc: RPC.Upstream.Caller,
        val call: ColdUpstreamCallDescription<REQUEST, CLIENT_STREAM, RESPONSE>,
    ) {
        private val streamEventSerializer = StreamEventSerializer(
            call.clientStreamSerializer,
            call.errorSerializer,
        )
        private val responseSerializer = ResponseSerializer(
            call.incomingSerializer,
            call.errorSerializer,
        )

        suspend fun run(payload: REQUEST, stream: Flow<CLIENT_STREAM>): RESPONSE {
            val serializedPayload = serializer.serialize(call.outgoingSerializer, payload)
            val serializedFlow = flow {
                try {
                    stream.collect {
                        emit(serializer.serialize(streamEventSerializer, StreamEvent.Element(it)))
                    }

                    emit(serializer.serialize(streamEventSerializer, StreamEvent.Complete()))
                } catch (t: Throwable) {
                    emit(serializer.serialize(streamEventSerializer, StreamEvent.Error(t)))
                }
            }
            val serializedResponse = rpc.perform(serializedPayload, serializedFlow)
            return when (val response = serializer.deserialize(responseSerializer, serializedResponse)) {
                is Response.Success -> response.response
                is Response.Error -> throw response.error.throwable()
            }
        }
    }
}