package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
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

public object ColdBistreamPendingRPC {
    public class Callee(
        protocol: AscensionRPCProtocol,
        context: CoroutineContext,
        reference: RPCReference,
        private val implementation: RPC.Bistream.Callee.Implementation,
    ): PendingRPC.Callee<AscensionRPCFrame.ColdBistream.Upstream, AscensionRPCFrame.ColdBistream.Downstream>(protocol, context, reference, logger), RPC.Bistream.Callee {
        private companion object {
            val logger = Logger<ColdBistreamPendingRPC.Callee>()
            // 60 seconds
            val flowStartTimeoutInMillis = 60 * 1000L
        }

        private sealed interface UpstreamState {
            object Created: UpstreamState
            class Started(val channel: Channel<SerializedPayload>): UpstreamState
            object Closed: UpstreamState
        }

        private sealed interface DownstreamState {
            object Created: DownstreamState
            class Opened(val flow: Flow<SerializedPayload>, val timeoutJob: Job): DownstreamState
            class Started(val job: Job): DownstreamState
            object Closed: DownstreamState
        }

        private val upstreamState = MutableStateFlow<UpstreamState>(UpstreamState.Created)
        private val downstreamState = MutableStateFlow<DownstreamState>(DownstreamState.Created)

        override val shouldComplete: Flow<Boolean> = combine(upstreamState, downstreamState) { upstream, downstream ->
            upstream == UpstreamState.Closed && downstream == DownstreamState.Closed
        }

        override suspend fun handle(frame: AscensionRPCFrame.ColdBistream.Upstream) {
            when (frame) {
                is AscensionRPCFrame.ColdBistream.Upstream.Open -> launch {
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

                    when (val serverStreamOrError = implementation.perform(frame.payload, clientStreamFlow)) {
                        is RPC.StreamOrError.Stream -> {
                            val timeoutJob = launch(start = CoroutineStart.LAZY) {
                                // The client should subscribe to the stream right away. They have 60 seconds before we close it.
                                delay(flowStartTimeoutInMillis)
                                // If the stream wasn't started by this time, we send the timeout error frame.
                                send(AscensionRPCFrame.ColdBistream.Downstream.StreamEvent.Timeout(flowStartTimeoutInMillis, reference))
                                downstreamState.value = DownstreamState.Closed
                            }
                            downstreamState.value = DownstreamState.Opened(serverStreamOrError.stream, timeoutJob)
                            send(AscensionRPCFrame.ColdBistream.Downstream.Opened(reference))
                            timeoutJob.start()
                        }
                        is RPC.StreamOrError.Error -> {
                            send(AscensionRPCFrame.ColdBistream.Downstream.Error(serverStreamOrError.error, reference))
                            downstreamState.value = DownstreamState.Closed
                        }
                    }
                }
                is AscensionRPCFrame.ColdBistream.Upstream.StreamOperation.Start -> {
                    when (val state = downstreamState.value) {
                        is DownstreamState.Opened -> {
                            launch(start = CoroutineStart.LAZY) {
                                state.flow.collect {
                                    send(AscensionRPCFrame.ColdBistream.Downstream.StreamEvent.Data(it, reference))
                                }
                            }.also {
                                state.timeoutJob.cancel()
                                downstreamState.value = DownstreamState.Started(it)
                                it.start()
                            }
                        }
                        DownstreamState.Created -> throw RPCProtocolViolationError("Stream is not ready. Cannot be started.")
                        is DownstreamState.Started -> throw RPCProtocolViolationError("Stream is already started. Cannot start again.")
                        DownstreamState.Closed -> throw RPCProtocolViolationError("Stream has been closed. Cannot start again.")
                    }
                }
                is AscensionRPCFrame.ColdBistream.Upstream.StreamOperation.Close -> {
                    when (val state = downstreamState.value) {
                        is DownstreamState.Started -> {
                            try {
                                state.job.cancel()
                            } catch (t: Throwable) {
                                logger.warning(t) { "Is it here?" }
                                throw t
                            }
                        }
                        DownstreamState.Created -> throw RPCProtocolViolationError("Stream not ready, cannot close.")
                        is DownstreamState.Opened -> {
                            logger.info { "Stream closed without starting it." }
                            try {
                                state.timeoutJob.cancel()
                            } catch (t: Throwable) {
                                logger.warning(t) { "2 Is it here?" }
                                throw t
                            }
                            downstreamState.value = DownstreamState.Closed
                        }
                        DownstreamState.Closed -> logger.warning { "Trying to close a closed stream, ignoring." }
                    }
                }
                is AscensionRPCFrame.ColdBistream.Upstream.StreamEvent.Data -> when (val state = upstreamState.value) {
                    is UpstreamState.Started -> state.channel.send(frame.data)
                    UpstreamState.Closed -> logger.warning { "Trying to send data to a closed stream, ignoring." }
                    UpstreamState.Created -> throw RPCProtocolViolationError("Stream has not been started yet, cannot accept data.")
                }
                is AscensionRPCFrame.ColdBistream.Upstream.StreamEvent.Timeout -> when (val state = upstreamState.value) {
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
            send(AscensionRPCFrame.ColdBistream.Downstream.StreamOperation.Start(reference))
        }

        private suspend fun closeClientStream() {
            send(AscensionRPCFrame.ColdBistream.Downstream.StreamOperation.Close(reference))
        }
    }

    public class Caller(
        protocol: AscensionRPCProtocol,
        context: CoroutineContext,
        private val serviceCallIdentifier: ServiceCallIdentifier,
        reference: RPCReference,
    ): PendingRPC.Caller<AscensionRPCFrame.ColdBistream.Downstream, AscensionRPCFrame.ColdBistream.Upstream>(protocol, context, reference, logger), RPC.Bistream.Caller {
        private companion object {
            val logger = Logger<ColdBistreamPendingRPC.Caller>()
            val streamTimeoutMillis = 60_000L
        }

        private val upstreamState = MutableStateFlow<UpstreamState>(UpstreamState.Created)
        private val downstreamState = MutableStateFlow<DownstreamState>(DownstreamState.Created)
        private val responseDeferred = CompletableDeferred<RPC.StreamOrError>(coroutineContext.job)

        override val shouldComplete: Flow<Boolean> = combine(upstreamState, downstreamState, responseDeferred.isCompletedFlow) { upstream, downstream, isCompleted ->
            upstream == UpstreamState.Closed && downstream == DownstreamState.Closed && isCompleted
        }

        private sealed interface UpstreamState {
            object Created: UpstreamState
            class Ready(val stream: Flow<SerializedPayload>): UpstreamState
            class WaitingToStart(val stream: Flow<SerializedPayload>, val timeoutJob: Job): UpstreamState
            class Started(val upstreamJob: Job): UpstreamState
            object Closed: UpstreamState
        }

        private sealed interface DownstreamState {
            object Created: DownstreamState
            class Opened(val channel: Channel<SerializedPayload>): DownstreamState
            object Closed: DownstreamState
        }

        override suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): RPC.StreamOrError {
            upstreamState.value = UpstreamState.Ready(stream)

            send(AscensionRPCFrame.ColdBistream.Upstream.Open(payload, serviceCallIdentifier, reference))

            return responseDeferred.await()
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        override suspend fun handle(frame: AscensionRPCFrame.ColdBistream.Downstream) {
            when (frame) {
                is AscensionRPCFrame.ColdBistream.Downstream.Opened -> when (downstreamState.value) {
                    is DownstreamState.Created -> {
                        val channel = newPayloadChannel()
                        responseDeferred.complete(
                            channel.consumeAsFlow()
                                .onStart {
                                    downstreamState.value = DownstreamState.Opened(channel)
                                    startServerStream()
                                }
                                .onCompletion {
                                    // TODO: !closedByUpstream?
                                    closeServerStream()
                                    downstreamState.value = DownstreamState.Closed
                                }
                                .let(RPC.StreamOrError::Stream)
                        )

                        val upstate = upstreamState.value
                        if (upstate is UpstreamState.Ready) {
                            val timeoutJob = launch {
                                delay(streamTimeoutMillis)
                                send(AscensionRPCFrame.ColdBistream.Upstream.StreamEvent.Timeout(streamTimeoutMillis, reference))
                                upstreamState.value = UpstreamState.Closed
                            }
                            upstreamState.value = UpstreamState.WaitingToStart(upstate.stream, timeoutJob)
                        }
                    }
                    DownstreamState.Closed -> throw RPCProtocolViolationError("Stream closed, cannot open again!")
                    is DownstreamState.Opened -> throw RPCProtocolViolationError("Response already received, cannot pass stream!")
                }
                is AscensionRPCFrame.ColdBistream.Downstream.StreamOperation.Start -> {
                    when (val state = upstreamState.value) {
                        is UpstreamState.Ready -> subscribeStream(state.stream)
                        is UpstreamState.WaitingToStart -> {
                            subscribeStream(state.stream)
                            state.timeoutJob.cancel()
                        }
                        UpstreamState.Created -> {
                            // This probably means we called `open` before setting the prepared stream, see `perform` method.
                            throw RPCProtocolViolationError("Upstream Client cannot start collecting before stream is prepared!.").throwable()
                        }
                        UpstreamState.Closed -> throw RPCProtocolViolationError("Cannot open closed stream.")
                        is UpstreamState.Started -> throw RPCProtocolViolationError("Cannot open an already started stream.")
                    }
                }
                is AscensionRPCFrame.ColdBistream.Downstream.StreamOperation.Close -> {
                    when (val state = upstreamState.value) {
                        is UpstreamState.Started -> {
                            state.upstreamJob.cancel()
                        }
                        UpstreamState.Closed -> logger.warning { "Trying to close an already closed upstream, ignoring." }
                        is UpstreamState.Ready -> {
                            logger.debug { "Closing a stream that was never started." }
                            upstreamState.value = UpstreamState.Closed
                        }
                        is UpstreamState.WaitingToStart -> {
                            logger.debug { "Closing a stream that was never started, but response for the call was received." }
                            state.timeoutJob.cancel()
                            upstreamState.value = UpstreamState.Closed
                        }
                        UpstreamState.Created -> throw RPCProtocolViolationError("Not yet initialized! Cannot close.")
                    }
                }
                is AscensionRPCFrame.ColdBistream.Downstream.Error -> {
                    if (responseDeferred.isCompleted) {
                        throw RPCProtocolViolationError("Response already received, cannot pass error!")
                    }
                    responseDeferred.complete(RPC.StreamOrError.Error(frame.payload))
                }
                is AscensionRPCFrame.ColdBistream.Downstream.StreamEvent.Data -> when (val state = downstreamState.value) {
                    is DownstreamState.Opened -> {
                        state.channel.send(frame.data)
                    }
                    DownstreamState.Closed -> throw RPCProtocolViolationError("Channel closed, can't accept more data!")
                    DownstreamState.Created -> throw RPCProtocolViolationError("Channel wasn't open. `Opened` frame is required before streaming data!")
                }
                is AscensionRPCFrame.ColdBistream.Downstream.StreamEvent.Timeout -> when (val state = downstreamState.value) {
                    is DownstreamState.Opened -> {
                        logger.error { "Stream timed out while opened. Please report this to Hyperdrive team." }
                        state.channel.close(StreamTimeoutException(frame.timeoutMillis))
                    }
                    DownstreamState.Closed -> {
                        logger.info { "Stream timed out while closed. Ignoring." }
                    }
                    DownstreamState.Created -> {
                        logger.warning { "Stream timed out." }
                        downstreamState.value = DownstreamState.Closed
                    }
                }
            }
        }

        private suspend fun startServerStream() {
            send(AscensionRPCFrame.ColdBistream.Upstream.StreamOperation.Start(reference))
        }

        private suspend fun closeServerStream() {
            send(AscensionRPCFrame.ColdBistream.Upstream.StreamOperation.Close(reference))
        }

        private fun subscribeStream(stream: Flow<SerializedPayload>) {
            val upstreamJob = launch(start = CoroutineStart.LAZY) {
                stream.collect {
                    send(AscensionRPCFrame.ColdBistream.Upstream.StreamEvent.Data(it, reference))
                }
            }
            upstreamState.value = UpstreamState.Started(upstreamJob)
            upstreamJob.invokeOnCompletion {
                logger.trace(it) { "Upstream job completed." }
                upstreamState.value = UpstreamState.Closed
            }
            upstreamJob.start()
        }
    }
}

