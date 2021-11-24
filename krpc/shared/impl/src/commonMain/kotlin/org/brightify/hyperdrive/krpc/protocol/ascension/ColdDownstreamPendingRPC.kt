package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.api.throwable
import org.brightify.hyperdrive.krpc.description.ColdDownstreamCallDescription
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.error.RPCError
import org.brightify.hyperdrive.krpc.error.RPCProtocolViolationError
import org.brightify.hyperdrive.krpc.error.RPCStreamTimeoutError
import org.brightify.hyperdrive.krpc.error.asRPCError
import org.brightify.hyperdrive.krpc.frame.AscensionRPCFrame
import org.brightify.hyperdrive.krpc.protocol.RPC
import org.brightify.hyperdrive.krpc.util.RPCReference
import org.brightify.hyperdrive.utils.Do
import kotlin.coroutines.cancellation.CancellationException

object ColdDownstreamPendingRPC {
    class Callee(
        protocol: AscensionRPCProtocol,
        scope: CoroutineScope,
        reference: RPCReference,
        private val implementation: RPC.Downstream.Callee.Implementation,
    ): PendingRPC.Callee<AscensionRPCFrame.ColdDownstream.Upstream, AscensionRPCFrame.ColdDownstream.Downstream>(protocol, scope, reference, logger), RPC.Downstream.Callee {
        private companion object {
            val logger = Logger<ColdDownstreamPendingRPC.Callee>()
            // 60 seconds
            val flowStartTimeoutInMillis = 60 * 1000L
        }

        private sealed class StreamState {
            object Created: StreamState()
            class Opened(val flow: Flow<SerializedPayload>): StreamState()
            class Started(val job: Job): StreamState()
            object Closed: StreamState()
        }

        private val serverStreamState = MutableStateFlow<StreamState>(StreamState.Created)

        override suspend fun handle(frame: AscensionRPCFrame.ColdDownstream.Upstream) {
            Do exhaustive when (frame) {
                is AscensionRPCFrame.ColdDownstream.Upstream.Open -> launch {
                    when (val serverStreamOrError = implementation.perform(frame.payload)) {
                        is RPC.StreamOrError.Stream -> {
                            serverStreamState.value = StreamState.Opened(serverStreamOrError.stream)
                            send(AscensionRPCFrame.ColdDownstream.Downstream.Opened(reference))

                            // The client should subscribe to the stream right away. They have 60 seconds before we close it.
                            val didTimeout = withTimeoutOrNull(flowStartTimeoutInMillis) {
                                serverStreamState.filterNot { it is StreamState.Opened }.first()
                                false
                            } ?: true

                            // If the stream wasn't started by this time, we send the timeout error frame.
                            if (didTimeout) {
                                throw RPCStreamTimeoutError(flowStartTimeoutInMillis).throwable()
                            }
                        }
                        is RPC.StreamOrError.Error -> {
                            serverStreamState.value = StreamState.Closed
                            send(AscensionRPCFrame.ColdDownstream.Downstream.Error(serverStreamOrError.error, reference))
                        }
                    }
                }
                is AscensionRPCFrame.ColdDownstream.Upstream.StreamOperation.Start -> {
                    Do exhaustive when (val state = serverStreamState.value) {
                        is StreamState.Opened -> {
                            launch(start = CoroutineStart.LAZY) {
                                state.flow.collect {
                                    send(AscensionRPCFrame.ColdDownstream.Downstream.StreamEvent(it, reference))
                                }
                            }.also {
                                serverStreamState.value = StreamState.Started(it)
                                it.start()
                            }
                        }
                        StreamState.Created -> throw RPCProtocolViolationError("Stream is not ready. Cannot be started.").throwable()
                        is StreamState.Started -> throw RPCProtocolViolationError("Stream is already started. Cannot start again.").throwable()
                        StreamState.Closed -> throw RPCProtocolViolationError("Stream has been closed. Cannot start again.").throwable()
                    }
                }
                is AscensionRPCFrame.ColdDownstream.Upstream.StreamOperation.Close -> {
                    Do exhaustive when (val state = serverStreamState.value) {
                        is StreamState.Started -> state.job.cancelAndJoin()
                        StreamState.Created -> throw RPCProtocolViolationError("Stream not ready, cannot close.").throwable()
                        is StreamState.Opened -> {
                            logger.info { "Stream closed without starting it." }
                            serverStreamState.value = StreamState.Closed
                        }
                        StreamState.Closed -> logger.warning { "Trying to close a closed stream, ignoring." }
                    }
                }
            }
        }
    }

    class Caller(
        protocol: AscensionRPCProtocol,
        scope: CoroutineScope,
        private val serviceCallIdentifier: ServiceCallIdentifier,
        reference: RPCReference,
    ): PendingRPC.Caller<AscensionRPCFrame.ColdDownstream.Downstream, AscensionRPCFrame.ColdDownstream.Upstream>(protocol, scope, reference, logger), RPC.Downstream.Caller {
        private companion object {
            val logger = Logger<ColdDownstreamPendingRPC.Caller>()
        }

        private val channelDeferred = CompletableDeferred<Channel<SerializedPayload>>()
        private val responseDeferred = CompletableDeferred<RPC.StreamOrError>()

        override suspend fun perform(payload: SerializedPayload): RPC.StreamOrError = withContext(this.coroutineContext) {
            send(AscensionRPCFrame.ColdDownstream.Upstream.Open(payload, serviceCallIdentifier, reference))

            responseDeferred.await()
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        override suspend fun handle(frame: AscensionRPCFrame.ColdDownstream.Downstream) {
            Do exhaustive when (frame) {
                is AscensionRPCFrame.ColdDownstream.Downstream.Opened -> {
                    if (responseDeferred.isCompleted) {
                        throw RPCProtocolViolationError("Response already received, cannot pass stream!").throwable()
                    }
                    val job = Job(coroutineContext.job)
                    val channel = Channel<SerializedPayload>().also { channel ->
                        job.invokeOnCompletion {
                            channel.close(it)
                        }
                    }
                    channelDeferred.complete(channel)
                    responseDeferred.complete(
                        channel.consumeAsFlow()
                            .onStart {
                                startServerStream()
                            }
                            .onCompletion {
                                // TODO: !closedByUpstream?
                                closeServerStream()
                                job.complete()
                            }
                            .let(RPC.StreamOrError::Stream)
                    )
                }
                is AscensionRPCFrame.ColdDownstream.Downstream.Error -> {
                    if (responseDeferred.isCompleted) {
                        throw RPCProtocolViolationError("Response already received, cannot pass error!").throwable()
                    }
                    responseDeferred.complete(RPC.StreamOrError.Error(frame.payload))
                }
                is AscensionRPCFrame.ColdDownstream.Downstream.StreamEvent -> if (channelDeferred.isCompleted) {
                    val channel = channelDeferred.getCompleted()
                    channel.send(frame.event)
                } else {
                    throw RPCProtocolViolationError("Channel wasn't open. `Opened` frame is required before streaming data!").throwable()
                }
            }
        }

        private suspend fun startServerStream() {
            send(AscensionRPCFrame.ColdDownstream.Upstream.StreamOperation.Start(reference))
        }

        private suspend fun closeServerStream() {
            send(AscensionRPCFrame.ColdDownstream.Upstream.StreamOperation.Close(reference))
        }
    }
}

object ColdDownstreamRunner {
    class Callee<REQUEST, SERVER_STREAM>(
        private val serializer: PayloadSerializer,
        private val call: RunnableCallDescription.ColdDownstream<REQUEST, SERVER_STREAM>,
    ): RPC.Downstream.Callee.Implementation {
        private val streamEventSerializer = StreamEventSerializer(
            call.responseSerializer,
            call.errorSerializer,
        )

        override suspend fun perform(payload: SerializedPayload): RPC.StreamOrError {
            val request = serializer.deserialize(call.requestSerializer, payload)

            return try {
                val stream = call.perform(request)
                flow {
                    try {
                        stream.collect {
                            emit(serializer.serialize(streamEventSerializer, StreamEvent.Element(it)))
                        }

                        emit(serializer.serialize(streamEventSerializer, StreamEvent.Complete()))
                    } catch (t: Throwable) {
                        emit(serializer.serialize(streamEventSerializer, StreamEvent.Error(t)))
                    }
                }.let(RPC.StreamOrError::Stream)
            } catch (t: Throwable) {
                RPC.StreamOrError.Error(serializer.serialize(call.errorSerializer, t.asRPCError()))
            }
        }
    }

    class Caller<REQUEST, SERVER_STREAM>(
        private val serializer: PayloadSerializer,
        private val rpc: RPC.Downstream.Caller,
        private val call: ColdDownstreamCallDescription<REQUEST, SERVER_STREAM>,
    ) {
        private val streamEventSerializer = StreamEventSerializer(
            call.serverStreamSerializer,
            call.errorSerializer,
        )

        suspend fun run(payload: REQUEST): Flow<SERVER_STREAM> {
            val serializedPayload = serializer.serialize(call.outgoingSerializer, payload)

            val serializedStreamOrError = rpc.perform(serializedPayload)
            return when (serializedStreamOrError) {
                is RPC.StreamOrError.Stream -> serializedStreamOrError.stream
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
                is RPC.StreamOrError.Error -> {
                    val error = serializer.deserialize(call.errorSerializer, serializedStreamOrError.error)
                    throw error.throwable()
                }
            }
        }
    }
}