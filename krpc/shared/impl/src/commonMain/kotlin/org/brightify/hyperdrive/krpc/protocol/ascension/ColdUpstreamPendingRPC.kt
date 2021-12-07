package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.api.throwable
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.util.RPCReference
import org.brightify.hyperdrive.krpc.error.RPCProtocolViolationError
import org.brightify.hyperdrive.krpc.frame.AscensionRPCFrame
import org.brightify.hyperdrive.krpc.protocol.RPC
import org.brightify.hyperdrive.utils.Do

object ColdUpstreamPendingRPC {
    class Callee(
        protocol: AscensionRPCProtocol,
        private val scope: CoroutineScope,
        reference: RPCReference,
        private val implementation: RPC.Upstream.Callee.Implementation,
    ): PendingRPC.Callee<AscensionRPCFrame.ColdUpstream.Upstream, AscensionRPCFrame.ColdUpstream.Downstream>(protocol, scope, reference, logger) {
        private companion object {
            val logger = Logger<ColdUpstreamPendingRPC.Callee>()
        }

        private val channel = Channel<SerializedPayload>()

        private val callCompleted = CompletableDeferred<Unit>()
        private val streamCompleted = CompletableDeferred<Unit>()

        init {
            scope.launch {
                listOf(callCompleted, streamCompleted).awaitAll()

                complete()
            }
        }

        override suspend fun handle(frame: AscensionRPCFrame.ColdUpstream.Upstream) {
            Do exhaustive when (frame) {
                is AscensionRPCFrame.ColdUpstream.Upstream.Open -> launch {
                    var streamConsumptionJob: CompletableJob? = null
                    val clientStreamFlow = channel.consumeAsFlow()
                        .onStart {
                            streamConsumptionJob = Job(scope.coroutineContext.job)
                            startClientStream()
                        }
                        .onCompletion {
                            closeClientStream()
                            streamConsumptionJob?.complete()
                        }

                    val response = implementation.perform(frame.payload, clientStreamFlow)
                    send(AscensionRPCFrame.ColdUpstream.Downstream.Response(response, reference))
                    callCompleted.complete(Unit)
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
            streamCompleted.complete(Unit)
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

        private val callCompleted = CompletableDeferred<Unit>()
        private val streamCompleted = CompletableDeferred<Unit>()

        override suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): SerializedPayload = try {
            withContext(this.coroutineContext) {
                this@Caller.stream = stream

                send(AscensionRPCFrame.ColdUpstream.Upstream.Open(payload, serviceCallIdentifier, reference))

                responseDeferred.await()
            }.also {
                callCompleted.complete(Unit)
            }
        } catch (t: Throwable) {
            complete()
            throw t
        }

        override suspend fun handle(frame: AscensionRPCFrame.ColdUpstream.Downstream) {
            Do exhaustive when (frame) {
                is AscensionRPCFrame.ColdUpstream.Downstream.StreamOperation.Start -> {
                    upstreamJob = launch {
                        if (!::stream.isInitialized) {
                            // This probably means we called `open` before setting the prepared stream, see `perform` method.
                            throw RPCProtocolViolationError("Upstream Client cannot start collecting before stream is prepared!.").throwable()
                        }

                        stream.collect {
                            send(AscensionRPCFrame.ColdUpstream.Upstream.StreamEvent(it, reference))
                        }
                    }
                    upstreamJob.invokeOnCompletion {
                        streamCompleted.complete(Unit)
                    }
                }
                is AscensionRPCFrame.ColdUpstream.Downstream.StreamOperation.Close -> {
                    if (!this::upstreamJob.isInitialized) {
                        throw RPCProtocolViolationError("Upstream Client's stream was not started. Cannot close.").throwable()
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
