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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.job
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.api.throwable
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.error.RPCProtocolViolationError
import org.brightify.hyperdrive.krpc.frame.AscensionRPCFrame
import org.brightify.hyperdrive.krpc.protocol.RPC
import org.brightify.hyperdrive.krpc.protocol.StreamTimeoutException
import org.brightify.hyperdrive.krpc.util.RPCReference

public object ColdBistreamPendingRPC {
    public class Callee(
        protocol: AscensionRPCProtocol,
        scope: CoroutineScope,
        reference: RPCReference,
        private val implementation: RPC.Bistream.Callee.Implementation,
    ): PendingRPC.Callee<AscensionRPCFrame.ColdBistream.Upstream, AscensionRPCFrame.ColdBistream.Downstream>(protocol, scope, reference, logger), RPC.Bistream.Callee {
        private companion object {
            val logger = Logger<ColdBistreamPendingRPC.Callee>()
            // 60 seconds
            val flowStartTimeoutInMillis = 60 * 1000L
        }

        private sealed interface StreamState {
            object Created: StreamState
            class Opened(val flow: Flow<SerializedPayload>, val timeoutJob: Job): StreamState
            class Started(val job: Job): StreamState
            object Closed: StreamState
        }

        private val clientStreamChannel = Channel<SerializedPayload>()
        private var serverStreamState: StreamState = StreamState.Created

        override suspend fun handle(frame: AscensionRPCFrame.ColdBistream.Upstream) {
            when (frame) {
                is AscensionRPCFrame.ColdBistream.Upstream.Open -> completionTracker.launch {
                    val serverStreamCompleted = completionTracker.acquire()
                    val clientStreamFlow = clientStreamChannel.consumeAsFlow()
                        .onStart {
                            startClientStream()
                        }
                        .onCompletion {
                            closeClientStream()
                            serverStreamCompleted.release()
                        }

                    when (val serverStreamOrError = implementation.perform(frame.payload, clientStreamFlow)) {
                        is RPC.StreamOrError.Stream -> {
                            val timeoutJob = completionTracker.launch(start = CoroutineStart.LAZY) {
                                // The client should subscribe to the stream right away. They have 60 seconds before we close it.
                                delay(flowStartTimeoutInMillis)
                                // If the stream wasn't started by this time, we send the timeout error frame.
                                send(AscensionRPCFrame.ColdBistream.Downstream.StreamEvent.Timeout(flowStartTimeoutInMillis, reference))
                                serverStreamState = StreamState.Closed
                            }
                            serverStreamState = StreamState.Opened(serverStreamOrError.stream, timeoutJob)
                            send(AscensionRPCFrame.ColdBistream.Downstream.Opened(reference))
                            timeoutJob.start()
                        }
                        is RPC.StreamOrError.Error -> {
                            serverStreamState = StreamState.Closed
                            send(AscensionRPCFrame.ColdBistream.Downstream.Error(serverStreamOrError.error, reference))
                        }
                    }
                }
                is AscensionRPCFrame.ColdBistream.Upstream.StreamOperation.Start -> {
                    when (val state = serverStreamState) {
                        is StreamState.Opened -> {
                            state.timeoutJob.cancel()
                            completionTracker.launch(start = CoroutineStart.LAZY) {
                                state.flow.collect {
                                    send(AscensionRPCFrame.ColdBistream.Downstream.StreamEvent.Data(it, reference))
                                }
                            }.also {
                                serverStreamState = StreamState.Started(it)
                                it.start()
                            }
                        }
                        StreamState.Created -> throw RPCProtocolViolationError("Stream is not ready. Cannot be started.")
                        is StreamState.Started -> throw RPCProtocolViolationError("Stream is already started. Cannot start again.")
                        StreamState.Closed -> throw RPCProtocolViolationError("Stream has been closed. Cannot start again.")
                    }
                }
                is AscensionRPCFrame.ColdBistream.Upstream.StreamOperation.Close -> completionTracker.launch {
                    when (val state = serverStreamState) {
                        is StreamState.Started -> state.job.cancelAndJoin()
                        StreamState.Created -> throw RPCProtocolViolationError("Stream not ready, cannot close.")
                        is StreamState.Opened -> {
                            logger.info { "Stream closed without starting it." }
                            state.timeoutJob.cancel()
                            serverStreamState = StreamState.Closed
                        }
                        StreamState.Closed -> logger.warning { "Trying to close a closed stream, ignoring." }
                    }
                }
                is AscensionRPCFrame.ColdBistream.Upstream.StreamEvent.Data -> {
                    clientStreamChannel.send(frame.data)
                }
                is AscensionRPCFrame.ColdBistream.Upstream.StreamEvent.Timeout -> {
                    clientStreamChannel.close(StreamTimeoutException(frame.timeoutMillis))
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
        scope: CoroutineScope,
        private val serviceCallIdentifier: ServiceCallIdentifier,
        reference: RPCReference,
    ): PendingRPC.Caller<AscensionRPCFrame.ColdBistream.Downstream, AscensionRPCFrame.ColdBistream.Upstream>(protocol, scope, reference, logger), RPC.Bistream.Caller {
        private companion object {
            val logger = Logger<ColdBistreamPendingRPC.Caller>()
            val streamTimeoutMillis = 60_000L
        }

        private var upstreamState: UpstreamState = UpstreamState.Created
        private var downstreamState: DownstreamState = DownstreamState.Created
        private val responseDeferred = CompletableDeferred<RPC.StreamOrError>(coroutineContext.job)

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

        override suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): RPC.StreamOrError = completionTracker.tracking {
            upstreamState = UpstreamState.Ready(stream)

            send(AscensionRPCFrame.ColdBistream.Upstream.Open(payload, serviceCallIdentifier, reference))

            responseDeferred.await()
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        override suspend fun handle(frame: AscensionRPCFrame.ColdBistream.Downstream) {
            when (frame) {
                is AscensionRPCFrame.ColdBistream.Downstream.Opened -> when (downstreamState) {
                    is DownstreamState.Created -> {
                        val trackingToken = completionTracker.acquire()
                        val channel = Channel<SerializedPayload>().also { channel ->
                            invokeOnCompletion {
                                channel.close(it)
                            }
                        }
                        responseDeferred.complete(
                            channel.consumeAsFlow()
                                .onStart {
                                    startServerStream()
                                }
                                .onCompletion {
                                    // TODO: !closedByUpstream?
                                    closeServerStream()
                                    trackingToken.release()
                                }
                                .let(RPC.StreamOrError::Stream)
                        )
                        downstreamState = DownstreamState.Opened(channel)

                        val upstate = upstreamState
                        if (upstate is UpstreamState.Ready) {
                            val timeoutJob = completionTracker.launch {
                                delay(streamTimeoutMillis)
                                send(AscensionRPCFrame.ColdBistream.Upstream.StreamEvent.Timeout(streamTimeoutMillis, reference))
                                upstreamState = UpstreamState.Closed
                            }
                            upstreamState = UpstreamState.WaitingToStart(upstate.stream, timeoutJob)
                        }
                    }
                    DownstreamState.Closed -> throw RPCProtocolViolationError("Stream closed, cannot open again!")
                    is DownstreamState.Opened -> throw RPCProtocolViolationError("Response already received, cannot pass stream!")
                }
                is AscensionRPCFrame.ColdBistream.Downstream.StreamOperation.Start -> {
                    when (val state = upstreamState) {
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
                is AscensionRPCFrame.ColdBistream.Downstream.StreamOperation.Close -> {
                    when (val state = upstreamState) {
                        is UpstreamState.Started -> {
                            state.upstreamJob.cancelAndJoin()
                        }
                        UpstreamState.Closed -> logger.warning { "Trying to close an already closed upstream, ignoring." }
                        is UpstreamState.Ready -> {
                            logger.debug { "Closing a stream that was never started." }
                            upstreamState = UpstreamState.Closed
                        }
                        is UpstreamState.WaitingToStart -> {
                            logger.debug { "Closing a stream that was never started, but response for the call was received." }
                            upstreamState = UpstreamState.Closed
                            state.timeoutJob.cancelAndJoin()
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
                is AscensionRPCFrame.ColdBistream.Downstream.StreamEvent.Data -> when (val state = downstreamState) {
                    is DownstreamState.Opened -> {
                        state.channel.send(frame.data)
                    }
                    DownstreamState.Closed -> throw RPCProtocolViolationError("Channel closed, can't accept more data!")
                    DownstreamState.Created -> throw RPCProtocolViolationError("Channel wasn't open. `Opened` frame is required before streaming data!")
                }
                is AscensionRPCFrame.ColdBistream.Downstream.StreamEvent.Timeout -> when (val state = downstreamState) {
                    is DownstreamState.Opened -> {
                        logger.error { "Stream timed out while opened. Please report this to Hyperdrive team." }
                        state.channel.close(StreamTimeoutException(frame.timeoutMillis))
                    }
                    DownstreamState.Closed -> {
                        logger.info { "Stream timed out while closed. Ignoring." }
                    }
                    DownstreamState.Created -> {
                        logger.warning { "Stream timed out." }
                        downstreamState = DownstreamState.Closed
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
            val upstreamJob = completionTracker.launch(start = CoroutineStart.LAZY) {
                stream.collect {
                    send(AscensionRPCFrame.ColdBistream.Upstream.StreamEvent.Data(it, reference))
                }
            }
            upstreamState = UpstreamState.Started(upstreamJob)
            upstreamJob.invokeOnCompletion {
                upstreamState = UpstreamState.Closed
            }
            upstreamJob.start()
        }
    }
}

