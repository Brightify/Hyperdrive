package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.util.RPCReference
import org.brightify.hyperdrive.krpc.error.RPCProtocolViolationError
import org.brightify.hyperdrive.krpc.error.RPCStreamTimeoutError
import org.brightify.hyperdrive.utils.Do
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.frame.AscensionRPCFrame
import org.brightify.hyperdrive.krpc.protocol.RPC

object ColdBistreamPendingRPC {
    class Callee(
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

        private sealed class StreamState {
            object Created: StreamState()
            class Opened(val flow: Flow<SerializedPayload>): StreamState()
            class Started(val job: Job): StreamState()
            object Closed: StreamState()
        }

        private val clientStreamChannel = Channel<SerializedPayload>()
        private val serverStreamState = MutableStateFlow<StreamState>(StreamState.Created)

        init {
            scope.launch {
                val clientStreamClosed = CompletableDeferred<Unit>()
                clientStreamChannel.invokeOnClose { clientStreamClosed.complete(Unit) }
                awaitAll(
                    clientStreamClosed,
                    async { serverStreamState.first { it == StreamState.Closed } },
                )

                complete()
            }
        }

        override suspend fun handle(frame: AscensionRPCFrame.ColdBistream.Upstream) {
            Do exhaustive when (frame) {
                is AscensionRPCFrame.ColdBistream.Upstream.Open -> launch {
                    var streamConsumptionJob: CompletableJob? = null
                    val clientStreamFlow = clientStreamChannel.consumeAsFlow()
                        .onStart {
                            streamConsumptionJob = Job(coroutineContext.job)
                            startClientStream()
                        }
                        .onCompletion {
                            closeClientStream()
                            streamConsumptionJob?.complete()
                        }

                    when (val serverStreamOrError = implementation.perform(frame.payload, clientStreamFlow)) {
                        is RPC.StreamOrError.Stream -> {
                            serverStreamState.value = StreamState.Opened(serverStreamOrError.stream)
                            send(AscensionRPCFrame.ColdBistream.Downstream.Opened(reference))

                            // The client should subscribe to the stream right away. They have 60 seconds before we close it.
                            val didTimeout = withTimeoutOrNull(flowStartTimeoutInMillis) {
                                serverStreamState.filterNot { it is StreamState.Opened }.first()
                                false
                            } ?: true

                            // If the stream wasn't started by this time, we send the timeout error frame.
                            if (didTimeout) {
                                throw RPCStreamTimeoutError(flowStartTimeoutInMillis)
                            }
                        }
                        is RPC.StreamOrError.Error -> {
                            serverStreamState.value = StreamState.Closed
                            send(AscensionRPCFrame.ColdBistream.Downstream.Error(serverStreamOrError.error, reference))
                        }
                    }
                }
                is AscensionRPCFrame.ColdBistream.Upstream.StreamOperation.Start -> {
                    Do exhaustive when (val state = serverStreamState.value) {
                        is StreamState.Opened -> {
                            launch(start = CoroutineStart.LAZY) {
                                state.flow.collect {
                                    send(AscensionRPCFrame.ColdBistream.Downstream.StreamEvent(it, reference))
                                }
                            }.also {
                                serverStreamState.value = StreamState.Started(it)
                                it.start()
                            }
                        }
                        StreamState.Created -> throw RPCProtocolViolationError("Stream is not ready. Cannot be started.")
                        is StreamState.Started -> throw RPCProtocolViolationError("Stream is already started. Cannot start again.")
                        StreamState.Closed -> throw RPCProtocolViolationError("Stream has been closed. Cannot start again.")
                    }
                }
                is AscensionRPCFrame.ColdBistream.Upstream.StreamOperation.Close -> {
                    Do exhaustive when (val state = serverStreamState.value) {
                        is StreamState.Started -> state.job.cancelAndJoin()
                        StreamState.Created -> throw RPCProtocolViolationError("Stream not ready, cannot close.")
                        is StreamState.Opened -> {
                            logger.info { "Stream closed without starting it." }
                            serverStreamState.value = StreamState.Closed
                        }
                        StreamState.Closed -> logger.warning { "Trying to close a closed stream, ignoring." }
                    }
                }
                is AscensionRPCFrame.ColdBistream.Upstream.StreamEvent -> {
                    clientStreamChannel.send(frame.event)
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

    class Caller(
        protocol: AscensionRPCProtocol,
        scope: CoroutineScope,
        private val serviceCallIdentifier: ServiceCallIdentifier,
        reference: RPCReference,
    ): PendingRPC.Caller<AscensionRPCFrame.ColdBistream.Downstream, AscensionRPCFrame.ColdBistream.Upstream>(protocol, scope, reference, logger), RPC.Bistream.Caller {
        private companion object {
            val logger = Logger<ColdBistreamPendingRPC.Caller>()
        }

        private lateinit var clientStream: Flow<SerializedPayload>
        private lateinit var clientStreamJob: Job
        private val serverChannelDeferred = CompletableDeferred<Channel<SerializedPayload>>()
        private val responseDeferred = CompletableDeferred<RPC.StreamOrError>()

        override suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): RPC.StreamOrError = withContext(this.coroutineContext) {
            this@Caller.clientStream = stream

            send(AscensionRPCFrame.ColdBistream.Upstream.Open(payload, serviceCallIdentifier, reference))

            responseDeferred.await()
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        override suspend fun handle(frame: AscensionRPCFrame.ColdBistream.Downstream) {
            Do exhaustive when (frame) {
                is AscensionRPCFrame.ColdBistream.Downstream.Opened -> {
                    if (responseDeferred.isCompleted) {
                        throw RPCProtocolViolationError("Response already received, cannot pass stream!")
                    }
                    val job = Job(coroutineContext.job)
                    val channel = Channel<SerializedPayload>()
                    job.invokeOnCompletion {
                        channel.close(it)
                    }
                    serverChannelDeferred.complete(channel)
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
                is AscensionRPCFrame.ColdBistream.Downstream.StreamOperation.Start -> clientStreamJob = launch {
                    if (!::clientStream.isInitialized) {
                        // This probably means we called `open` before setting the prepared stream, see `perform` method.
                        throw RPCProtocolViolationError("Upstream Client cannot start collecting before stream is prepared!.")
                    }

                    clientStream.collect {
                        send(AscensionRPCFrame.ColdBistream.Upstream.StreamEvent(it, reference))
                    }
                }
                is AscensionRPCFrame.ColdBistream.Downstream.StreamOperation.Close -> {
                    if (!this::clientStreamJob.isInitialized) {
                        throw RPCProtocolViolationError("Upstream Client's stream was not started. Cannot close.")
                    }
                    clientStreamJob.cancelAndJoin()
                }
                is AscensionRPCFrame.ColdBistream.Downstream.Error -> {
                    if (responseDeferred.isCompleted) {
                        throw RPCProtocolViolationError("Response already received, cannot pass error!")
                    }
                    responseDeferred.complete(RPC.StreamOrError.Error(frame.payload))
                }
                is AscensionRPCFrame.ColdBistream.Downstream.StreamEvent -> if (serverChannelDeferred.isCompleted) {
                    val channel = serverChannelDeferred.getCompleted()
                    channel.send(frame.event)
                } else {
                    throw RPCProtocolViolationError("Channel wasn't open. `Opened` frame is required before streaming data!")
                }
            }
        }

        private suspend fun startServerStream() {
            send(AscensionRPCFrame.ColdBistream.Upstream.StreamOperation.Start(reference))
        }

        private suspend fun closeServerStream() {
            send(AscensionRPCFrame.ColdBistream.Upstream.StreamOperation.Close(reference))
        }
    }
}

