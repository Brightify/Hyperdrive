package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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

public object ColdUpstreamPendingRPC {
    public class Callee(
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
            when (frame) {
                is AscensionRPCFrame.ColdUpstream.Upstream.Open -> completionTracker.launch {
                    val streamCompleted = completionTracker.acquire()
                    val clientStreamFlow = channel.consumeAsFlow()
                        .onStart {
                            startClientStream()
                        }
                        .onCompletion {
                            closeClientStream()
                            streamCompleted.release()
                        }

                    val response = implementation.perform(frame.payload, clientStreamFlow)
                    send(AscensionRPCFrame.ColdUpstream.Downstream.Response(response, reference))
                }
                is AscensionRPCFrame.ColdUpstream.Upstream.StreamEvent.Data -> completionTracker.launch {
                    channel.send(frame.data)
                }
                is AscensionRPCFrame.ColdUpstream.Upstream.StreamEvent.Timeout -> {
                    channel.close(StreamTimeoutException(frame.timeoutMillis))
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
        scope: CoroutineScope,
        private val serviceCallIdentifier: ServiceCallIdentifier,
        reference: RPCReference,
    ): PendingRPC.Caller<AscensionRPCFrame.ColdUpstream.Downstream, AscensionRPCFrame.ColdUpstream.Upstream>(protocol, scope, reference, logger), RPC.Upstream.Caller {
        private companion object {
            val logger = Logger<ColdUpstreamPendingRPC.Caller>()
            val streamTimeoutMillis = 60_000L
        }

        private val responseDeferred = CompletableDeferred<SerializedPayload>(coroutineContext.job)
        private var streamState: UpstreamState = UpstreamState.Created

        override suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): SerializedPayload = completionTracker.tracking {
            streamState = UpstreamState.Ready(stream)

            send(AscensionRPCFrame.ColdUpstream.Upstream.Open(payload, serviceCallIdentifier, reference))

            responseDeferred.await()
        }

        override suspend fun handle(frame: AscensionRPCFrame.ColdUpstream.Downstream) {
            when (frame) {
                is AscensionRPCFrame.ColdUpstream.Downstream.StreamOperation.Start -> {
                    when (val state = streamState) {
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
                    when (val state = streamState) {
                        is UpstreamState.Started -> {
                            state.upstreamJob.cancelAndJoin()
                        }
                        UpstreamState.Closed -> logger.warning { "Trying to close an already closed upstream, ignoring." }
                        is UpstreamState.Ready -> {
                            logger.debug { "Closing a stream that was never started." }
                            streamState = UpstreamState.Closed
                        }
                        is UpstreamState.WaitingToStart -> {
                            logger.debug { "Closing a stream that was never started, but response for the call was received." }
                            streamState = UpstreamState.Closed
                            state.timeoutJob.cancelAndJoin()
                        }
                        UpstreamState.Created -> throw RPCProtocolViolationError("Not yet initialized! Cannot close.")
                    }
                }
                is AscensionRPCFrame.ColdUpstream.Downstream.Response -> {
                    responseDeferred.complete(frame.payload)
                    val state = streamState
                    if (state is UpstreamState.Ready) {
                        val timeoutJob = completionTracker.launch {
                            delay(streamTimeoutMillis)
                            send(AscensionRPCFrame.ColdUpstream.Upstream.StreamEvent.Timeout(streamTimeoutMillis, reference))
                            streamState = UpstreamState.Closed
                        }
                        streamState = UpstreamState.WaitingToStart(state.stream, timeoutJob)
                    }
                }
            }
        }

        private fun subscribeStream(stream: Flow<SerializedPayload>) {
            val upstreamJob = completionTracker.launch(start = CoroutineStart.LAZY) {
                stream.collect {
                    send(AscensionRPCFrame.ColdUpstream.Upstream.StreamEvent.Data(it, reference))
                }
            }
            streamState = UpstreamState.Started(upstreamJob)
            upstreamJob.invokeOnCompletion {
                streamState = UpstreamState.Closed
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
