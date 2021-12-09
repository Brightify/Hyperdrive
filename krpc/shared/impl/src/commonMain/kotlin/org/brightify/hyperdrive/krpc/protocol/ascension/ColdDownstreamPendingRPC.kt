package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.error.RPCProtocolViolationError
import org.brightify.hyperdrive.krpc.frame.AscensionRPCFrame
import org.brightify.hyperdrive.krpc.protocol.RPC
import org.brightify.hyperdrive.krpc.protocol.StreamTimeoutException
import org.brightify.hyperdrive.krpc.util.RPCReference
import kotlin.coroutines.CoroutineContext

public object ColdDownstreamPendingRPC {
    public class Callee(
        protocol: AscensionRPCProtocol,
        context: CoroutineContext,
        reference: RPCReference,
        private val implementation: RPC.Downstream.Callee.Implementation,
    ): PendingRPC.Callee<AscensionRPCFrame.ColdDownstream.Upstream, AscensionRPCFrame.ColdDownstream.Downstream>(protocol, context, reference, logger), RPC.Downstream.Callee {
        private companion object {
            val logger = Logger<ColdDownstreamPendingRPC.Callee>()
            // 60 seconds
            val flowStartTimeoutInMillis = 60 * 1000L
        }

        private sealed interface StreamState {
            object Created: StreamState
            class Opened(val flow: Flow<SerializedPayload>, val timeoutJob: Job): StreamState
            class Started(val job: Job): StreamState
            object Closed: StreamState
        }
        private sealed interface DownstreamState {
            object Created: DownstreamState
            class Opened(val flow: Flow<SerializedPayload>, val timeoutJob: Job): DownstreamState
            class Started(val job: Job): DownstreamState
            object Closed: DownstreamState
        }

        private val downstreamState = MutableStateFlow<DownstreamState>(DownstreamState.Created)

        override val shouldComplete: Flow<Boolean> = downstreamState.map { downstream ->
            downstream == DownstreamState.Closed
        }

        override suspend fun handle(frame: AscensionRPCFrame.ColdDownstream.Upstream) {
            when (frame) {
                is AscensionRPCFrame.ColdDownstream.Upstream.Open -> launch {
                    when (val serverStreamOrError = implementation.perform(frame.payload)) {
                        is RPC.StreamOrError.Stream -> {
                            val timeoutJob = launch(start = CoroutineStart.LAZY) {
                                // The client should subscribe to the stream right away. They have 60 seconds before we close it.
                                delay(flowStartTimeoutInMillis)

                                // If the stream wasn't started by this time, we send the timeout error frame.
                                send(AscensionRPCFrame.ColdDownstream.Downstream.StreamEvent.Timeout(flowStartTimeoutInMillis, reference))
                                downstreamState.value = DownstreamState.Closed
                            }
                            downstreamState.value = DownstreamState.Opened(serverStreamOrError.stream, timeoutJob)
                            send(AscensionRPCFrame.ColdDownstream.Downstream.Opened(reference))
                            timeoutJob.start()
                        }
                        is RPC.StreamOrError.Error -> {
                            send(AscensionRPCFrame.ColdDownstream.Downstream.Error(serverStreamOrError.error, reference))
                            downstreamState.value = DownstreamState.Closed
                        }
                    }
                }
                is AscensionRPCFrame.ColdDownstream.Upstream.StreamOperation.Start -> {
                    when (val state = downstreamState.value) {
                        is DownstreamState.Opened -> {
                            launch(start = CoroutineStart.LAZY) {
                                state.flow.collect {
                                    send(AscensionRPCFrame.ColdDownstream.Downstream.StreamEvent.Data(it, reference))
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
                is AscensionRPCFrame.ColdDownstream.Upstream.StreamOperation.Close -> {
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
            }
        }
    }

    public class Caller(
        protocol: AscensionRPCProtocol,
        context: CoroutineContext,
        private val serviceCallIdentifier: ServiceCallIdentifier,
        reference: RPCReference,
    ): PendingRPC.Caller<AscensionRPCFrame.ColdDownstream.Downstream, AscensionRPCFrame.ColdDownstream.Upstream>(protocol, context, reference, logger), RPC.Downstream.Caller {
        private companion object {
            val logger = Logger<ColdDownstreamPendingRPC.Caller>()
        }

        private sealed interface DownstreamState {
            object Created: DownstreamState
            class Opened(val channel: Channel<SerializedPayload>): DownstreamState
            object Closed: DownstreamState
        }

        private val downstreamState = MutableStateFlow<DownstreamState>(DownstreamState.Created)
        private val responseDeferred = CompletableDeferred<RPC.StreamOrError>(coroutineContext.job)

        override val shouldComplete: Flow<Boolean> = combine(downstreamState, responseDeferred.isCompletedFlow) { downstream, isCompleted ->
            downstream == DownstreamState.Closed && isCompleted
        }
        override suspend fun perform(payload: SerializedPayload): RPC.StreamOrError {
            send(AscensionRPCFrame.ColdDownstream.Upstream.Open(payload, serviceCallIdentifier, reference))

            return responseDeferred.await()
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        override suspend fun handle(frame: AscensionRPCFrame.ColdDownstream.Downstream) {
            when (frame) {
                is AscensionRPCFrame.ColdDownstream.Downstream.Opened -> when (downstreamState.value) {
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
                    }
                    DownstreamState.Closed -> throw RPCProtocolViolationError("Stream closed, cannot open again!")
                    is DownstreamState.Opened -> throw RPCProtocolViolationError("Response already received, cannot pass stream!")
                }
                is AscensionRPCFrame.ColdDownstream.Downstream.Error -> {
                    if (responseDeferred.isCompleted) {
                        throw RPCProtocolViolationError("Response already received, cannot pass error!")
                    }
                    responseDeferred.complete(RPC.StreamOrError.Error(frame.payload))
                }
                is AscensionRPCFrame.ColdDownstream.Downstream.StreamEvent.Data -> when (val state = downstreamState.value) {
                    is DownstreamState.Opened -> {
                        state.channel.send(frame.data)
                    }
                    DownstreamState.Closed -> throw RPCProtocolViolationError("Channel closed, can't accept more data!")
                    DownstreamState.Created -> throw RPCProtocolViolationError("Channel wasn't open. `Opened` frame is required before streaming data!")
                }
                is AscensionRPCFrame.ColdDownstream.Downstream.StreamEvent.Timeout -> when (val state = downstreamState.value) {
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
            send(AscensionRPCFrame.ColdDownstream.Upstream.StreamOperation.Start(reference))
        }

        private suspend fun closeServerStream() {
            send(AscensionRPCFrame.ColdDownstream.Upstream.StreamOperation.Close(reference))
        }
    }
}
