package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.api.throwable
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.error.RPCProtocolViolationError
import org.brightify.hyperdrive.krpc.frame.AscensionRPCFrame
import org.brightify.hyperdrive.krpc.protocol.RPC
import org.brightify.hyperdrive.krpc.protocol.StreamTimeoutException
import org.brightify.hyperdrive.krpc.util.RPCReference
import kotlin.coroutines.CoroutineContext

public object ColdUpstreamPendingRPC {
    public class Callee(
        protocol: AscensionRPCProtocol,
        context: CoroutineContext,
        reference: RPCReference,
        private val implementation: RPC.Upstream.Callee.Implementation,
    ): PendingRPC.Callee<AscensionRPCFrame.ColdUpstream.Upstream, AscensionRPCFrame.ColdUpstream.Downstream>(protocol, context, reference, logger) {
        private companion object {
            val logger = Logger<ColdUpstreamPendingRPC.Callee>()
        }

        private sealed interface UpstreamState {
            object Created: UpstreamState
            class Started(val channel: Channel<SerializedPayload>): UpstreamState
            object Closed: UpstreamState
        }

        private sealed interface ResponseState {
            object Created: ResponseState
            object Completed: ResponseState
        }

        private val upstreamState = MutableStateFlow<UpstreamState>(UpstreamState.Created)
        private val responseState = MutableStateFlow<ResponseState>(ResponseState.Created)

        override val shouldComplete: Flow<Boolean> = combine(upstreamState, responseState) { upstream, response ->
            upstream == UpstreamState.Closed && response == ResponseState.Completed
        }

        override suspend fun handle(frame: AscensionRPCFrame.ColdUpstream.Upstream) {
            when (frame) {
                is AscensionRPCFrame.ColdUpstream.Upstream.Open -> launch {
                    val clientStreamChannel = newPayloadChannel()
                    val clientStreamFlow = clientStreamChannel.consumeAsFlow()
                        .onStart {
                            upstreamState.value = UpstreamState.Started(clientStreamChannel)
                            startClientStream()
                        }
                        .onCompletion {
                            closeClientStream()
                            upstreamState.value = UpstreamState.Closed
                        }

                    val response = implementation.perform(frame.payload, clientStreamFlow)
                    send(AscensionRPCFrame.ColdUpstream.Downstream.Response(response, reference))
                    responseState.value = ResponseState.Completed
                }
                is AscensionRPCFrame.ColdUpstream.Upstream.StreamEvent.Data -> when (val state = upstreamState.value) {
                    is UpstreamState.Started -> state.channel.send(frame.data)
                    UpstreamState.Closed -> logger.warning { "Trying to send data to a closed stream, ignoring." }
                    UpstreamState.Created -> throw RPCProtocolViolationError("Stream has not been started yet, cannot accept data.")
                }
                is AscensionRPCFrame.ColdUpstream.Upstream.StreamEvent.Timeout -> when (val state = upstreamState.value) {
                    UpstreamState.Closed -> logger.warning { "Trying to send timeout to a closed stream, ignoring." }
                    UpstreamState.Created -> {
                        logger.info { "Stream has timed out." }
                        upstreamState.value = UpstreamState.Closed
                    }
                    is UpstreamState.Started -> {
                        logger.info { "Stream has timed out." }
                        state.channel.close(StreamTimeoutException(frame.timeoutMillis))
                        upstreamState.value = UpstreamState.Closed
                    }
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

    public class Caller(
        protocol: AscensionRPCProtocol,
        context: CoroutineContext,
        private val serviceCallIdentifier: ServiceCallIdentifier,
        reference: RPCReference,
    ): PendingRPC.Caller<AscensionRPCFrame.ColdUpstream.Downstream, AscensionRPCFrame.ColdUpstream.Upstream>(protocol, context, reference, logger), RPC.Upstream.Caller {
        private companion object {
            val logger = Logger<ColdUpstreamPendingRPC.Caller>()
            val streamTimeoutMillis = 60_000L
        }

        private val upstreamState = MutableStateFlow<UpstreamState>(UpstreamState.Created)
        private val responseDeferred = CompletableDeferred<SerializedPayload>(coroutineContext.job)

        override val shouldComplete: Flow<Boolean> = combine(upstreamState, responseDeferred.isCompletedFlow) { upstream, isCompleted ->
            upstream == UpstreamState.Closed && isCompleted
        }

        override suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): SerializedPayload {
            upstreamState.value = UpstreamState.Ready(stream)

            send(AscensionRPCFrame.ColdUpstream.Upstream.Open(payload, serviceCallIdentifier, reference))

            return responseDeferred.await()
        }

        override suspend fun handle(frame: AscensionRPCFrame.ColdUpstream.Downstream) {
            when (frame) {
                is AscensionRPCFrame.ColdUpstream.Downstream.StreamOperation.Start -> {
                    when (val state = upstreamState.value) {
                        is UpstreamState.Ready -> subscribeStream(state.stream)
                        is UpstreamState.WaitingToStart -> {
                            state.timeoutJob.cancel()
                            subscribeStream(state.stream)
                        }
                        UpstreamState.Created -> {
                            // This probably means we called `open` before setting the prepared stream, see `perform` method.
                            throw RPCProtocolViolationError("Upstream Client cannot start collecting before stream is prepared!.").throwable()
                        }
                        UpstreamState.Closed -> throw RPCProtocolViolationError("Cannot open closed stream.")
                        is UpstreamState.Started -> throw RPCProtocolViolationError("Cannot open an already started stream.")
                    }
                }
                is AscensionRPCFrame.ColdUpstream.Downstream.StreamOperation.Close -> {
                    when (val state = upstreamState.value) {
                        is UpstreamState.Started -> {
                            state.upstreamJob.cancelAndJoin()
                        }
                        UpstreamState.Closed -> logger.warning { "Trying to close an already closed upstream, ignoring." }
                        is UpstreamState.Ready -> {
                            logger.debug { "Closing a stream that was never started." }
                            upstreamState.value = UpstreamState.Closed
                        }
                        is UpstreamState.WaitingToStart -> {
                            logger.debug { "Closing a stream that was never started, but response for the call was received." }
                            upstreamState.value = UpstreamState.Closed
                            state.timeoutJob.cancelAndJoin()
                        }
                        UpstreamState.Created -> throw RPCProtocolViolationError("Not yet initialized! Cannot close.")
                    }
                }
                is AscensionRPCFrame.ColdUpstream.Downstream.Response -> {
                    responseDeferred.complete(frame.payload)
                    val state = upstreamState.value
                    if (state is UpstreamState.Ready) {
                        val timeoutJob = launch {
                            delay(streamTimeoutMillis)
                            send(AscensionRPCFrame.ColdUpstream.Upstream.StreamEvent.Timeout(streamTimeoutMillis, reference))
                            upstreamState.value = UpstreamState.Closed
                        }
                        upstreamState.value = UpstreamState.WaitingToStart(state.stream, timeoutJob)
                    }
                }
            }
        }

        private fun subscribeStream(stream: Flow<SerializedPayload>) {
            val upstreamJob = launch(start = CoroutineStart.LAZY) {
                stream.collect {
                    send(AscensionRPCFrame.ColdUpstream.Upstream.StreamEvent.Data(it, reference))
                }
            }
            upstreamState.value = UpstreamState.Started(upstreamJob)
            upstreamJob.invokeOnCompletion {
                upstreamState.value = UpstreamState.Closed
            }
            upstreamJob.start()
        }

        private sealed interface UpstreamState {
            object Created: UpstreamState
            class Ready(val stream: Flow<SerializedPayload>): UpstreamState
            class WaitingToStart(val stream: Flow<SerializedPayload>, val timeoutJob: Job): UpstreamState
            class Started(val upstreamJob: Job): UpstreamState
            object Closed: UpstreamState
        }
    }
}
