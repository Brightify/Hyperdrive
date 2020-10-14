package org.brightify.hyperdrive.client.impl

import io.ktor.utils.io.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.client.api.ServiceClient
import org.brightify.hyperdrive.krpc.api.ClientCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdBistreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdDownstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdUpstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.api.RPCEvent
import org.brightify.hyperdrive.krpc.api.RPCFrame
import org.brightify.hyperdrive.krpc.api.RPCReference
import org.brightify.hyperdrive.krpc.api.RPCTransport
import kotlin.collections.set


class ServiceClientImpl(
    private val transport: RPCTransport<RPCEvent.Upstream, RPCEvent.Downstream>,
    private val communicationScope: CoroutineScope,
    private val outStreamScope: CoroutineScope
): ServiceClient {

    private var callReferenceCounter: RPCReference = RPCReference.MIN_VALUE
    private val pendingRequests: MutableMap<Int, PendingRPC<Any>> = mutableMapOf()
    private val runJob: Job

    init {
        runJob = communicationScope.launch {
            while (isActive) {
                try {
                    val frame = transport.receive()
                    println("Client - $frame")
                    val exhaustive = when (val event = frame.header.event) {
                        is RPCEvent.Downstream.Opened -> {
                            // TODO this means the server accepted the call, we can start sending data now if we have a stream
                            val pendingCall = pendingRequests.getOrElse(frame.header.callReference) {
                                TODO()
                            }

                            pendingCall.stateManager.setOpened()
                        }
                        is RPCEvent.Downstream.Response,
                        is RPCEvent.Downstream.Data -> {
                            val pendingCall = pendingRequests.getOrElse(frame.header.callReference) {
                                TODO()
                            }

                            val data = frame.decoder.decodeSerializableValue(pendingCall.deserializationStrategy)
                            pendingCall.accept(data)
                        }
                        is RPCEvent.Downstream.Error, is RPCEvent.Downstream.Warning -> {
                            val pendingCall = pendingRequests.getOrElse(frame.header.callReference) {
                                TODO()
                            }

                            val throwable = frame.decoder.decodeSerializableValue(pendingCall.errorSerializer)
                            pendingCall.reject(throwable)
                        }
                        is RPCEvent.Downstream.Close -> {
                            val pendingCall = pendingRequests.getOrElse(frame.header.callReference) {
                                TODO()
                            }

                            // TODO: Figure out how to send errors
//                            val throwable = frame.decoder.decodeNullableSerializableValue(serializer<Throwable?>())
                            pendingCall.close(null) // throwable)
                        }
                    }
                } catch (e: ClosedReceiveChannelException) {
                    // Disconnected from server, wait and then try again
                    delay(500)
                } catch (e: EOFException) {
                    // Disconnected from server, wait and then try again
                    delay(500)
                } catch (t: Throwable) {
                    t.printStackTrace()
                    throw t
                }
            }
        }
    }

    override suspend fun <REQUEST, RESPONSE> singleCall(serviceCall: ClientCallDescriptor<REQUEST, RESPONSE>, request: REQUEST): RESPONSE {
        val event = RPCEvent.Upstream.Open(serviceCall.identifier)
        val frame = OutgoingRPCFrame(
            header = RPCFrame.Header(
                callReference = nextCallReference(),
                event = event,
            ),
            serializationStrategy = serviceCall.outgoingSerializer as SerializationStrategy<Any?>,
            data = request,
        )

        val deferred = CompletableDeferred<RESPONSE>()
        val pendingCall = object: PendingSingleCall<RESPONSE>(serviceCall.incomingSerializer, serviceCall.errorSerializer) {
            override suspend fun accept(data: RESPONSE) {
                deferred.complete(data)
            }

            override suspend fun reject(throwable: Throwable) {
                deferred.completeExceptionally(throwable)
            }

            override suspend fun close(throwable: Throwable?) {
                deferred.cancel(CancellationException("Server closed the connection unexpectedly!", throwable))
            }
        }

        pendingRequests[frame.header.callReference] = pendingCall as PendingRPC<Any>

        transport.send(frame)

        return deferred.await()
    }

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> clientStream(serviceCall: ColdUpstreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>, request: REQUEST, clientStream: Flow<CLIENT_STREAM>): RESPONSE {
        val event = RPCEvent.Upstream.Open(serviceCall.identifier)
        val callReference = nextCallReference()
        val frame = OutgoingRPCFrame(
            header = RPCFrame.Header(
                callReference = callReference,
                event = event
            ),
            serializationStrategy = serviceCall.outgoingSerializer as SerializationStrategy<Any?>,
            data = request
        )

        var streamingJob: Job? = null
        val deferred = CompletableDeferred<RESPONSE>()
        val openStream = object: OpenOutStream<CLIENT_STREAM, RESPONSE>(
            serializationStrategy = serviceCall.clientStreamSerializer,
            deserializationStrategy = serviceCall.incomingSerializer,
            errorSerializer = serviceCall.errorSerializer,
            clientStream
        ) {
            override suspend fun accept(data: RESPONSE) {
                deferred.complete(data)
            }

            override suspend fun reject(throwable: Throwable) {
                deferred.completeExceptionally(throwable)
            }

            override suspend fun close(throwable: Throwable?) {
                val capturedStreamingJob = streamingJob
                if (capturedStreamingJob != null) {
                    capturedStreamingJob.cancel(CancellationException("Server closed the connection.", throwable))
                } else {
                    streamingJob = Job()
                }
            }
        }

        pendingRequests[frame.header.callReference] = openStream as PendingRPC<Any>

        transport.send(frame)
        openStream.stateManager.await(PendingRPC.State.Ready)

        if (streamingJob == null) {
            streamingJob = outStreamScope.launch {
                try {
                    clientStream.collect {
                        transport.send(
                            OutgoingRPCFrame(
                                header = RPCFrame.Header(
                                    callReference = callReference,
                                    event = RPCEvent.Upstream.Data
                                ),
                                serializationStrategy = serviceCall.clientStreamSerializer as SerializationStrategy<Any?>,
                                data = it
                            )
                        )
                    }

                    transport.send(
                        OutgoingRPCFrame(
                            header = RPCFrame.Header(
                                callReference = callReference,
                                event = RPCEvent.Upstream.Close
                            ),
                            serializationStrategy = Unit.serializer() as SerializationStrategy<Any?>,
                            data = Unit
                        )
                    )
                } catch (t: Throwable) {
                    transport.send(
                        OutgoingRPCFrame(
                            header = RPCFrame.Header(
                                callReference = callReference,
                                event = RPCEvent.Upstream.Error,
                            ),
                            serializationStrategy = serviceCall.errorSerializer as SerializationStrategy<Any?>,
                            data = t,
                        )
                    )
                }
            }
        } else {
            error("streamingJob was set, probably from a different thread. That's illegal.")
        }

        return deferred.await()
    }

    override suspend fun <REQUEST, RESPONSE> serverStream(
        serviceCall: ColdDownstreamCallDescriptor<REQUEST, RESPONSE>,
        request: REQUEST
    ): Flow<RESPONSE> = flow {
        val event = RPCEvent.Upstream.Open(serviceCall.identifier)
        val callReference = nextCallReference()
        val frame = OutgoingRPCFrame(
            header = RPCFrame.Header(
                callReference = callReference,
                event = event
            ),
            serializationStrategy = serviceCall.outgoingSerializer as SerializationStrategy<Any?>,
            data = request
        )

        val streamChannel = Channel<RESPONSE>()
        val openStream = object: OpenInStream<RESPONSE>(
            deserializationStrategy = serviceCall.serverStreamSerializer,
            errorSerializer = serviceCall.errorSerializer,
        ) {
            override suspend fun accept(data: RESPONSE) {
                streamChannel.send(data)
            }

            override suspend fun reject(throwable: Throwable) {
                streamChannel.close(throwable)
            }

            override suspend fun close(throwable: Throwable?) {
                streamChannel.close(throwable)
            }
        }

        pendingRequests[frame.header.callReference] = openStream as PendingRPC<Any>

        transport.send(frame)

        emitAll(streamChannel)
    }

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> biStream(
        serviceCall: ColdBistreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>,
        request: REQUEST,
        clientStream: Flow<CLIENT_STREAM>
    ): Flow<RESPONSE> = flow {
        val event = RPCEvent.Upstream.Open(serviceCall.identifier)
        val callReference = nextCallReference()
        val frame = OutgoingRPCFrame(
            header = RPCFrame.Header(
                callReference = callReference,
                event = event
            ),
            serializationStrategy = serviceCall.outgoingSerializer as SerializationStrategy<Any?>,
            data = request
        )

        var streamingJob: Job? = null
        val streamChannel = Channel<RESPONSE>()
        val openStream = object: OpenInStream<RESPONSE>(
            deserializationStrategy = serviceCall.serverStreamSerializer,
            errorSerializer = serviceCall.errorSerializer,
        ) {
            override suspend fun accept(data: RESPONSE) {
                streamChannel.send(data)
            }

            override suspend fun reject(throwable: Throwable) {
                streamChannel.close(throwable)
            }

            override suspend fun close(throwable: Throwable?) {
                val capturedStreamingJob = streamingJob
                if (capturedStreamingJob != null) {
                    capturedStreamingJob.cancel(CancellationException("Server closed the connection.", throwable))
                } else {
                    streamingJob = Job()
                }
                streamChannel.close(throwable)
            }
        }

        pendingRequests[frame.header.callReference] = openStream as PendingRPC<Any>

        transport.send(frame)

        openStream.stateManager.await(PendingRPC.State.Ready)

        if (streamingJob == null) {
            streamingJob = outStreamScope.launch {
                try {
                    clientStream.collect {
                        transport.send(
                            OutgoingRPCFrame(
                                header = RPCFrame.Header(
                                    callReference = callReference,
                                    event = RPCEvent.Upstream.Data
                                ),
                                serializationStrategy = serviceCall.clientStreamSerializer as SerializationStrategy<Any?>,
                                data = it
                            )
                        )
                    }

                    transport.send(
                        OutgoingRPCFrame(
                            header = RPCFrame.Header(
                                callReference = callReference,
                                event = RPCEvent.Upstream.Close
                            ),
                            serializationStrategy = Unit.serializer() as SerializationStrategy<Any?>,
                            data = Unit
                        )
                    )
                } catch (t: Throwable) {
                    transport.send(
                        OutgoingRPCFrame(
                            header = RPCFrame.Header(
                                callReference = callReference,
                                event = RPCEvent.Upstream.Error,
                            ),
                            serializationStrategy = serviceCall.errorSerializer as SerializationStrategy<Any?>,
                            data = t,
                        )
                    )
                }
            }
        } else {
            error("streamingJob was set, probably from a different thread. That's illegal.")
        }

        emitAll(streamChannel)
    }

    override fun shutdown() {
        runJob.cancel()
    }

    private fun nextCallReference(): Int {
        return callReferenceCounter++
    }
}
