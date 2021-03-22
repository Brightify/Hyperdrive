package org.brightify.hyperdrive.krpc.api.impl

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdDownstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.DownstreamRPCEvent
import org.brightify.hyperdrive.krpc.api.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.api.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.api.RPCConnection
import org.brightify.hyperdrive.krpc.api.RPCFrame
import org.brightify.hyperdrive.krpc.api.RPCReference
import org.brightify.hyperdrive.krpc.api.UnexpectedRPCEventException
import org.brightify.hyperdrive.krpc.api.UpstreamRPCEvent
import org.brightify.hyperdrive.krpc.api.error.RPCProtocolViolationError
import org.brightify.hyperdrive.krpc.api.error.RPCStreamTimeoutError
import org.brightify.hyperdrive.krpc.api.throwable
import kotlin.coroutines.cancellation.CancellationException

object ColdDownstreamPendingRPC {
    private val <REQUEST, SERVER_STREAM> CallDescriptor.ColdDownstream<REQUEST, SERVER_STREAM>.serverStreamEventSerializer: KSerializer<out StreamEvent<out SERVER_STREAM>>
        get() = StreamEventSerializer(responseSerializer, errorSerializer)

    private val <REQUEST, SERVER_STREAM> ColdDownstreamCallDescriptor<REQUEST, SERVER_STREAM>.serverStreamEventSerializer: KSerializer<out StreamEvent<out SERVER_STREAM>>
        get() = StreamEventSerializer(serverStreamSerializer, errorSerializer)

    class Server<REQUEST, SERVER_STREAM>(
        connection: RPCConnection,
        reference: RPCReference,
        call: CallDescriptor.ColdDownstream<REQUEST, SERVER_STREAM>,
        onFinished: () -> Unit,
    ): _PendingRPC.Server<REQUEST, CallDescriptor.ColdDownstream<REQUEST, SERVER_STREAM>>(connection, reference, call, onFinished) {
        private companion object {
            val logger = Logger<ColdDownstreamPendingRPC.Server<*, *>>()
            // 60 seconds
            val flowStartTimeoutInMillis = 60 * 1000L
        }

        private val outgoingFlowState = MutableStateFlow<OutgoingFlowState<SERVER_STREAM>>(OutgoingFlowState.Created())

        sealed class OutgoingFlowState<SERVER_STREAM> {
            class Created<SERVER_STREAM>: OutgoingFlowState<SERVER_STREAM>()
            class Opened<SERVER_STREAM>(val flow: Flow<SERVER_STREAM>): OutgoingFlowState<SERVER_STREAM>()
            class Started<SERVER_STREAM>(val job: Job): OutgoingFlowState<SERVER_STREAM>()
            class Closed<SERVER_STREAM>: OutgoingFlowState<SERVER_STREAM>()
        }

        override suspend fun handle(frame: IncomingRPCFrame<UpstreamRPCEvent>) {
            Do exhaustive when (frame.header.event) {
                is UpstreamRPCEvent.Open -> launch {
                    val data = frame.decoder.decodeSerializableValue(call.requestSerializer)

                    val flow = call.perform(data)

                    outgoingFlowState.value = OutgoingFlowState.Opened(flow)
                    frame.confirmOpened()

                    // The client should subscribe to the stream right away. They have 60 seconds before we close it.
                    val didTimeout = withTimeoutOrNull(flowStartTimeoutInMillis) {
                        outgoingFlowState.filterNot { it is OutgoingFlowState.Opened }.first()
                        false
                    } ?: true

                    // If the stream wasn't started by this time, we send the timeout error frame.
                    if (didTimeout) {
                        throw RPCStreamTimeoutError(flowStartTimeoutInMillis)
                    }
                }
                UpstreamRPCEvent.StreamOperation.Start -> {
                    when (val state = outgoingFlowState.value) {
                        is OutgoingFlowState.Opened -> {
                            val job = launch {
                                state.flow
                                    .catch { exception ->
                                        frame.sendStreamEvent(StreamEvent.Error(exception))
                                    }
                                    .collect {
                                        frame.sendStreamEvent(StreamEvent.Next(it))
                                    }

                                frame.sendStreamEvent(StreamEvent.Complete())
                            }

                            outgoingFlowState.value = OutgoingFlowState.Started(job)
                        }
                        is OutgoingFlowState.Created -> {
                            cancel("Flow is not ready yet.")
                        }
                        is OutgoingFlowState.Started -> {
                            cancel("Flow is already being collected.")
                        }
                        is OutgoingFlowState.Closed -> {
                            cancel("Flow has been collected.")
                        }
                    }
                }
                UpstreamRPCEvent.StreamOperation.Close -> {
                    when (val state = outgoingFlowState.value) {
                        is OutgoingFlowState.Started -> {
                            state.job.cancelAndJoin()
                        }
                        is OutgoingFlowState.Opened -> {
                            outgoingFlowState.value = OutgoingFlowState.Closed()
                        }
                        is OutgoingFlowState.Created -> {
                            throw UnexpectedRPCEventException(UpstreamRPCEvent.StreamOperation.Start::class, "Stream not ready.")
                        }
                        is OutgoingFlowState.Closed -> {
                            frame.warnUnexpected("Stream was already closed. Ignoring.")
                        }
                    }
                }
                UpstreamRPCEvent.Warning -> {
                    val error = errorSerializer.decodeThrowable(frame.decoder)
                    logger.warning(error) { "Received a warning from the client." }
                }
                UpstreamRPCEvent.Error -> {
                    val error = errorSerializer.decodeThrowable(frame.decoder)
                    cancel("Error received from the client.", error)
                }
                UpstreamRPCEvent.Cancel -> {
                    cancel("Cancelled by the client.")
                }
                UpstreamRPCEvent.Data -> {
                    throw RPCProtocolViolationError("Downstream call does not accept Data frames.")
                }
            }
        }

        private suspend fun IncomingRPCFrame<UpstreamRPCEvent>.confirmOpened() {
            connection.send(OutgoingRPCFrame(
                RPCFrame.Header(header.callReference, DownstreamRPCEvent.Opened),
                Unit.serializer(),
                Unit,
            ))
        }

        private suspend fun IncomingRPCFrame<UpstreamRPCEvent>.sendStreamEvent(event: StreamEvent<SERVER_STREAM>) {
            connection.send(OutgoingRPCFrame(
                RPCFrame.Header(header.callReference, DownstreamRPCEvent.Data),
                call.serverStreamEventSerializer,
                event
            ))
        }
    }

    class Client<REQUEST, SERVER_STREAM>(
        connection: RPCConnection,
        reference: RPCReference,
        call: ColdDownstreamCallDescriptor<REQUEST, SERVER_STREAM>,
        onFinished: () -> Unit,
    ): _PendingRPC.Client<REQUEST, Flow<SERVER_STREAM>, ColdDownstreamCallDescriptor<REQUEST, SERVER_STREAM>>(connection, reference, call, onFinished) {
        private companion object {
            val logger = Logger<ColdDownstreamPendingRPC.Client<*, *>>()
        }

        private val channelDeferred = CompletableDeferred<Channel<SERVER_STREAM>>()
        private val flowDeferred = CompletableDeferred<Flow<SERVER_STREAM>>()
        private var closedByUpstream = false

        override suspend fun perform(payload: REQUEST): Flow<SERVER_STREAM> = run {
            open(payload)

            flowDeferred.await()
        }

        override suspend fun handle(frame: IncomingRPCFrame<DownstreamRPCEvent>) {
            Do exhaustive when (frame.header.event) {
                DownstreamRPCEvent.Opened -> {
                    retain()
                    val channel = Channel<SERVER_STREAM>()
                    channelDeferred.complete(channel)
                    flowDeferred.complete(
                        channel.consumeAsFlow()
                            .onStart { frame.startServerStream() }
                            .onCompletion {
                                println("$closedByUpstream, $this, $it")
                                if (!closedByUpstream) {
                                    frame.closeServerStream()
                                    release()
                                }
                            }
                    )
                }
                DownstreamRPCEvent.Data -> {
                    if (channelDeferred.isCompleted) {
                        val event = frame.decoder.decodeSerializableValue(call.serverStreamEventSerializer)
                        val channel = channelDeferred.getCompleted()
                        Do exhaustive when (event) {
                            is StreamEvent.Next -> channel.send(event.data)
                            is StreamEvent.Complete -> {
                                closedByUpstream = true
                                channel.close()
                            }
                            is StreamEvent.Error -> {
                                closedByUpstream = true
                                channel.close(event.error.throwable())
                            }
                        }
                    } else {
                        throw RPCProtocolViolationError("Channel wasn't open. `Opened` frame is required before streaming data!")
                    }
                }
                DownstreamRPCEvent.Response -> {
                    throw RPCProtocolViolationError("Downstream call requires data stream, not")
                }
                is DownstreamRPCEvent.StreamOperation -> {
                    throw RPCProtocolViolationError("Downstream call doesn't support receiving stream operations from the server as there is no upstream to control.")
                }
                DownstreamRPCEvent.Warning -> {
                    val error = errorSerializer.decodeThrowable(frame.decoder)
                    logger.warning(error) { "Received a warning from the server." }
                }
                DownstreamRPCEvent.Error -> {
                    val error = errorSerializer.decodeThrowable(frame.decoder)
                    cancel("Server error", error)
                }
            }
        }

        private suspend fun IncomingRPCFrame<DownstreamRPCEvent>.startServerStream() {
            connection.send(OutgoingRPCFrame(
                RPCFrame.Header(header.callReference, UpstreamRPCEvent.StreamOperation.Start),
                Unit.serializer(),
                Unit,
            ))
        }

        private suspend fun IncomingRPCFrame<DownstreamRPCEvent>.closeServerStream() {
            connection.send(OutgoingRPCFrame(
                RPCFrame.Header(header.callReference, UpstreamRPCEvent.StreamOperation.Close),
                Unit.serializer(),
                Unit,
            ))
        }
    }
}