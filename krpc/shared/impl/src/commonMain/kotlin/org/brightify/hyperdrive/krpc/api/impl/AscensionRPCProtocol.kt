package org.brightify.hyperdrive.krpc.api.impl

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.serializer
import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.ClientCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdBistreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdDownstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdUpstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ContextUpdateRPCEvent
import org.brightify.hyperdrive.krpc.api.DownstreamRPCEvent
import org.brightify.hyperdrive.krpc.api.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.api.LocalOutStreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.api.RPCFrame
import org.brightify.hyperdrive.krpc.api.RPCProtocol
import org.brightify.hyperdrive.krpc.api.RPCReference
import org.brightify.hyperdrive.krpc.api.RPCConnection
import org.brightify.hyperdrive.krpc.api.RPCEvent
import org.brightify.hyperdrive.krpc.api.UnexpectedRPCEventException
import org.brightify.hyperdrive.krpc.api.UpstreamRPCEvent
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.api.error.UnknownRPCReferenceException

class AscensionRPCProtocol(
    private val serviceRegistry: ServiceRegistry,
    private val connection: RPCConnection,
    private val outStreamScope: CoroutineScope,
    private val responseScope: CoroutineScope,
): RPCProtocol {
    override val version = RPCProtocol.Version.Ascension

    override val isActive: Boolean
        get() = connection.isActive

    private val serverPendingCalls = mutableMapOf<RPCReference, _PendingRPC.Server<*, *>>()
    private val serverJobs = mutableMapOf<RPCReference, Job>()
    private val openStreams = mutableMapOf<RPCReference, Stream>()
    private val baseRPCErrorSerializer = RPCErrorSerializer()

    private val clientPendingCalls = mutableMapOf<RPCReference, _PendingRPC.Client<*, *, *>>()

    // TODO: Replace with AtomicInt
    private var callReferenceCounter: RPCReference = RPCReference.MIN_VALUE
    private val pendingRequests: MutableMap<Int, PendingRPC<Any, Any>> = mutableMapOf()

    private val receivingJob: Job

    init {
        receivingJob = connection.launch {
            while (isActive) {
                val frame = connection.receive()
                @Suppress("UNUSED_VARIABLE")
                val exhaustive: Unit = when (val event = frame.header.event) {
                    is ContextUpdateRPCEvent -> {
                        TODO("Update context")
                    }
                    is DownstreamRPCEvent -> handleDownstreamEvent(event, frame)
                    is UpstreamRPCEvent -> handleUpstreamEvent(event, frame)
                    else -> {
                        closeWithError(frame.header.callReference, UnexpectedRPCEventException(event::class))
                    }
                }
            }
        }
    }

    private suspend fun handleDownstreamEvent(event: DownstreamRPCEvent, frame: IncomingRPCFrame<RPCEvent>) {
        val pendingCall = clientPendingCalls[frame.header.callReference] ?: return run {
            if (event != DownstreamRPCEvent.Error) {
                sendUnknownReferenceError(frame.header.callReference)
            }
        }
        pendingCall.accept(frame as IncomingRPCFrame<DownstreamRPCEvent>)

        // Do exhaustive when (event) {
        //     DownstreamRPCEvent.Opened -> {
        //         // TODO: this means the server accepted the call, we can start sending data now if we have a stream
        //         val pendingCall = pendingRequests[frame.header.callReference]
        //         if (pendingCall == null) {
        //             sendUnknownReferenceError(frame.header.callReference)
        //             return
        //         }
        //
        //         pendingCall.stateManager.setOpened()
        //     }
        //     DownstreamRPCEvent.Response, DownstreamRPCEvent.Data -> {
        //         val pendingCall = pendingRequests[frame.header.callReference]
        //         if (pendingCall == null) {
        //             sendUnknownReferenceError(frame.header.callReference)
        //             return
        //         }
        //
        //         val data = frame.decoder.decodeSerializableValue(pendingCall.deserializationStrategy)
        //         val canAcceptMore = pendingCall.accept(data)
        //         if (!canAcceptMore) {
        //             pendingRequests.remove(frame.header.callReference)
        //         }
        //         return
        //     }
        //     DownstreamRPCEvent.DataEnd -> {
        //         val pendingCall = pendingRequests[frame.header.callReference]
        //         if (pendingCall == null) {
        //             sendUnknownReferenceError(frame.header.callReference)
        //             return
        //         }
        //         pendingCall.dataEnd()
        //     }
        //     DownstreamRPCEvent.Error, DownstreamRPCEvent.Warning -> {
        //         val pendingCall = pendingRequests.remove(frame.header.callReference)
        //         if (pendingCall == null) {
        //             sendUnknownReferenceError(frame.header.callReference)
        //             return
        //         }
        //
        //         val throwable = frame.decoder.decodeSerializableValue(pendingCall.errorSerializer)
        //         pendingCall.reject(throwable)
        //     }
        //     DownstreamRPCEvent.Close -> {
        //         val pendingCall = pendingRequests[frame.header.callReference]
        //         if (pendingCall == null) {
        //             // TODO: Do we want to report this to the sender? Closing a non-existent call probably means it got removed by accepting its last event.
        //             // sendUnknownReferenceError(frame.header.callReference)
        //             return
        //         }
        //         val pendingCallInvalid = pendingCall.close(null)
        //         if (pendingCallInvalid) {
        //             pendingRequests.remove(frame.header.callReference)
        //         }
        //         return
        //     }
        // }
    }

    private suspend fun handleUpstreamEvent(event: UpstreamRPCEvent, frame: IncomingRPCFrame<RPCEvent>) {
        suspend fun IncomingRPCFrame<RPCEvent>.respond(
            event: DownstreamRPCEvent,
            serializer: KSerializer<out Any?>,
            data: Any?,
        ) = connection.send(
            OutgoingRPCFrame(
                header = RPCFrame.Header(
                    this.header.callReference,
                    event,
                ),
                serializationStrategy = serializer as SerializationStrategy<Any?>,
                data,
            )
        )

        val reference = frame.header.callReference
        val existingPendingCall = serverPendingCalls[reference]
        val pendingCall = if (existingPendingCall != null) {
            existingPendingCall
        } else if (event is UpstreamRPCEvent.Open) {
            val call = serviceRegistry.getCallById(event.serviceCall, CallDescriptor::class)
            val newPendingCall = when (call) {
                is CallDescriptor.Single<*, *> -> SingleCallPendingRPC.Server(
                    connection,
                    reference,
                    call,
                ) {
                    println("Server - Single Finished")
                    serverPendingCalls.remove(reference)
                }
                is CallDescriptor.ColdUpstream<*, *, *> -> ColdUpstreamPendingRPC.Server(
                    connection,
                    reference,
                    call,
                ) {
                    println("Server - ColdUpstream Finished")
                    serverPendingCalls.remove(reference)
                }
                is CallDescriptor.ColdDownstream<*, *> -> ColdDownstreamPendingRPC.Server(
                    connection,
                    reference,
                    call,
                ) {
                    println("Server - ColdDownstream Finished")
                    serverPendingCalls.remove(reference)
                }
                is CallDescriptor.ColdBistream<*, *, *> -> ColdBistreamPendingRPC.Server(
                    connection,
                    reference,
                    call,
                ) {
                    println("Server - ColdBistream Finished")
                    serverPendingCalls.remove(reference)
                }
                null -> TODO("Call identifier doesn't exist")
            }

            serverPendingCalls[reference] = newPendingCall
            newPendingCall
        } else {
            // We don't want to be stuck in a loop of sending the same error up and down.
            if (event != UpstreamRPCEvent.Error) {
                sendUnknownReferenceError(frame.header.callReference)
            }
            return
        }

        pendingCall.accept(frame as IncomingRPCFrame<UpstreamRPCEvent>)

        // when (event) {
        //     is UpstreamRPCEvent.Open -> {
        //         val call = serviceRegistry.getCallById(event.serviceCall, CallDescriptor::class)
        //         Do exhaustive when (call) {
        //             is CallDescriptor.Single<*, *> -> {
        //                 // TODO: Save Job for cancellation
        //                 serverJobs[frame.header.callReference] = responseScope.launch {
        //                     val call = call as CallDescriptor.Single<Any?, Any?>
        //                     val data = frame.decoder.decodeSerializableValue(call.requestSerializer)
        //                     try {
        //                         frame.respond(
        //                             DownstreamRPCEvent.Response,
        //                             call.responseSerializer,
        //                             call.perform(data),
        //                         )
        //                     } catch (t: Throwable) {
        //                         frame.respond(
        //                             DownstreamRPCEvent.Error,
        //                             call.errorSerializer,
        //                             t,
        //                         )
        //                     }
        //                 }
        //             }
        //             is CallDescriptor.ColdUpstream<*, *, *> -> {
        //                 val call = call as CallDescriptor.ColdUpstream<Any?, Any?, Any?>
        //                 val data = frame.decoder.decodeSerializableValue(call.requestSerializer as DeserializationStrategy<Any?>)
        //                 val channel = Channel<Any?>()
        //
        //                 openStreams[frame.header.callReference] = Stream(
        //                     channel,
        //                     call.clientStreamSerializer as DeserializationStrategy<Any?>,
        //                     call.errorSerializer,
        //                 )
        //
        //                 // TODO: Save Job for cancellation
        //                 serverJobs[frame.header.callReference] = responseScope.launch {
        //                     try {
        //                         frame.respond(
        //                             DownstreamRPCEvent.Response,
        //                             call.responseSerializer,
        //                             call.perform(data, channel.consumeAsFlow()
        //                                 .onStart {
        //                                     frame.respond(
        //                                         DownstreamRPCEvent.Opened,
        //                                         Unit.serializer(),
        //                                         Unit,
        //                                     )
        //                                 }
        //                                 .onCompletion { exception ->
        //                                     if (exception == null) {
        //                                         frame.respond(
        //                                             DownstreamRPCEvent.Close,
        //                                             Unit.serializer(),
        //                                             Unit,
        //                                         )
        //                                     }
        //                                 }),
        //                         )
        //                     } catch (e: CancellationException) {
        //                         e.printStackTrace()
        //                     } catch (t: Throwable) {
        //                         frame.respond(
        //                             DownstreamRPCEvent.Error,
        //                             call.errorSerializer,
        //                             t,
        //                         )
        //                     }
        //                 }
        //             }
        //             is CallDescriptor.ColdDownstream<*, *> -> {
        //                 val call = call as CallDescriptor.ColdDownstream<Any?, Any?>
        //                 val data = frame.decoder.decodeSerializableValue(call.requestSerializer as DeserializationStrategy<Any?>)
        //
        //                 // TODO: Save Job for cancellation
        //                 serverJobs[frame.header.callReference] = responseScope.launch {
        //                     try {
        //                         val flow = call.perform(data)
        //
        //                         frame.respond(
        //                             DownstreamRPCEvent.Opened,
        //                             Unit.serializer(),
        //                             Unit,
        //                         )
        //
        //                         flow
        //                             .onCompletion {
        //                                 frame.respond(
        //                                     DownstreamRPCEvent.Close,
        //                                     Unit.serializer(),
        //                                     Unit,
        //                                 )
        //                             }
        //                             .collect { data ->
        //                                 frame.respond(
        //                                     DownstreamRPCEvent.Data,
        //                                     call.responseSerializer as KSerializer<Any?>,
        //                                     data,
        //                                 )
        //                             }
        //                     } catch (t: Throwable) {
        //                         frame.respond(
        //                             DownstreamRPCEvent.Error,
        //                             call.errorSerializer,
        //                             t,
        //                         )
        //                     }
        //                 }
        //             }
        //             is CallDescriptor.ColdBistream<*, *, *> -> {
        //                 val call = call as CallDescriptor.ColdBistream<Any?, Any?, Any?>
        //                 val data = frame.decoder.decodeSerializableValue(call.requestSerializer as DeserializationStrategy<Any?>)
        //                 val channel = Channel<Any?>()
        //
        //                 openStreams[frame.header.callReference] = Stream(
        //                     channel,
        //                     call.clientStreamSerializer as DeserializationStrategy<Any?>,
        //                     call.errorSerializer,
        //                 )
        //
        //                 // TODO: Save Job for cancellation
        //                 serverJobs[frame.header.callReference] = responseScope.launch {
        //                     try {
        //                         val flow = call.perform(data, channel.consumeAsFlow()
        //                             .onStart {
        //                                 frame.respond(
        //                                     DownstreamRPCEvent.Opened,
        //                                     Unit.serializer(),
        //                                     Unit,
        //                                 )
        //                             }
        //                             .onCompletion { exception ->
        //                                 if (exception == null) {
        //                                     frame.respond(
        //                                         DownstreamRPCEvent.Close,
        //                                         Unit.serializer(),
        //                                         Unit,
        //                                     )
        //                                 }
        //                             })
        //
        //                         flow
        //                             .onCompletion { exception ->
        //                                 if (exception == null) {
        //                                     frame.respond(
        //                                         DownstreamRPCEvent.DataEnd,
        //                                         Unit.serializer(),
        //                                         Unit,
        //                                     )
        //                                 }
        //                             }
        //                             .collect { data ->
        //                                 frame.respond(
        //                                     DownstreamRPCEvent.Data,
        //                                     call.responseSerializer as KSerializer<Any?>,
        //                                     data,
        //                                 )
        //                             }
        //                     } catch (e: CancellationException) {
        //                         e.printStackTrace()
        //                     } catch (t: Throwable) {
        //                         frame.respond(
        //                             DownstreamRPCEvent.Error,
        //                             call.errorSerializer,
        //                             t,
        //                         )
        //                     }
        //                 }
        //             }
        //             null ->
        //                 responseScope.launch {
        //                     frame.respond(
        //                         DownstreamRPCEvent.Error,
        //                         baseRPCErrorSerializer,
        //                         NotImplementedError("Not implemented!"),
        //                     )
        //                 }
        //         }
        //     }
        //     UpstreamRPCEvent.Data -> {
        //         openStreams[frame.header.callReference]?.accept(frame.decoder) ?: responseScope.launch {
        //             frame.respond(DownstreamRPCEvent.Error, UnknownRPCReferenceException.serializer(), UnknownRPCReferenceException(frame.header.callReference))
        //         }
        //     }
        //     UpstreamRPCEvent.DataEnd -> {
        //         openStreams[frame.header.callReference]?.dataEnd() ?: responseScope.launch {
        //             frame.respond(DownstreamRPCEvent.Error, UnknownRPCReferenceException.serializer(), UnknownRPCReferenceException(frame.header.callReference))
        //         }
        //     }
        //     UpstreamRPCEvent.Error -> {
        //         // TODO: Send error if we can't find the reference
        //         openStreams.remove(frame.header.callReference)?.reject(frame.decoder) ?: responseScope.launch {
        //             frame.respond(DownstreamRPCEvent.Error, UnknownRPCReferenceException.serializer(), UnknownRPCReferenceException(frame.header.callReference))
        //         }
        //     }
        //     is UpstreamRPCEvent.Close -> {
        //         openStreams[frame.header.callReference]?.close() ?: responseScope.launch {
        //             frame.respond(DownstreamRPCEvent.Error, UnknownRPCReferenceException.serializer(), UnknownRPCReferenceException(frame.header.callReference))
        //         }
        //     }
        //     UpstreamRPCEvent.Cancel -> {
        //         // It's okay to ignore the reference not existing as it means this call is already cancelled.
        //         serverJobs.remove(frame.header.callReference)?.cancel()
        //     }
        // }
    }

    suspend fun detach() {
        receivingJob.cancelAndJoin()
    }

    override suspend fun join() {
        receivingJob.join()
    }

    override suspend fun close() {
        detach()
        connection.close()
    }

    override suspend fun <REQUEST, RESPONSE> singleCall(serviceCall: ClientCallDescriptor<REQUEST, RESPONSE>, request: REQUEST): RESPONSE {
        val reference = nextCallReference()
        val pendingCall = SingleCallPendingRPC.Client(connection, serviceCall, reference) {
            println("Client - Single finished")
            clientPendingCalls.remove(reference)
        }
        clientPendingCalls[reference] = pendingCall

        return pendingCall.perform(request)

        // val deferred = CompletableDeferred<RESPONSE>()
        //
        // val pendingCall = object: PendingSingleCall<REQUEST, RESPONSE>(serviceCall, request) {
        //     override suspend fun accept(data: RESPONSE): Boolean {
        //         deferred.complete(data)
        //         return false
        //     }
        //
        //     override suspend fun dataEnd() {
        //         deferred.completeExceptionally(CancellationException("Server sent no data!"))
        //     }
        //
        //     override suspend fun reject(throwable: Throwable) {
        //         deferred.completeExceptionally(throwable)
        //     }
        //
        //     override suspend fun close(throwable: Throwable?): Boolean {
        //         deferred.cancel(CancellationException("Server closed the connection unexpectedly!", throwable))
        //         return true
        //     }
        // }
        //
        // run(pendingCall)
        //
        // return deferred.await()
    }

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> clientStream(serviceCall: ColdUpstreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>, request: REQUEST, clientStream: Flow<CLIENT_STREAM>): RESPONSE {
        val reference = nextCallReference()
        val pendingCall = ColdUpstreamPendingRPC.Client(connection, reference, serviceCall, clientStream) {
            clientPendingCalls.remove(reference)
        }
        clientPendingCalls[reference] = pendingCall

        return pendingCall.perform(request)

        // var streamingJob: Job? = null
        // val deferred = CompletableDeferred<RESPONSE>()
        // val openStream = object: OpenOutStream<REQUEST, CLIENT_STREAM, RESPONSE>(serviceCall, request, clientStream) {
        //     override suspend fun accept(data: RESPONSE): Boolean {
        //         deferred.complete(data)
        //         return false
        //     }
        //
        //     override suspend fun dataEnd() {
        //         deferred.completeExceptionally(CancellationException("Server sent no data!"))
        //     }
        //
        //     override suspend fun reject(throwable: Throwable) {
        //         deferred.completeExceptionally(throwable)
        //     }
        //
        //     override suspend fun close(throwable: Throwable?): Boolean {
        //         val capturedStreamingJob = streamingJob
        //         if (capturedStreamingJob != null) {
        //             capturedStreamingJob.cancel(CancellationException("Server closed the connection.", throwable))
        //         } else {
        //             // FIXME: This is a workaround when server closes the connection right away before we get to creating it ourselves.
        //             streamingJob = Job()
        //         }
        //         return false
        //     }
        // }
        //
        // val callReference = run(openStream)
        //
        // openStream.stateManager.await(PendingRPC.State.Ready)
        //
        // if (streamingJob == null) {
        //     streamingJob = subscribeOutStream(callReference, serviceCall, clientStream)
        // } else {
        //     error("streamingJob was set, probably from a different thread. That's illegal.")
        // }
        //
        // return deferred.await()
    }

    override suspend fun <REQUEST, RESPONSE> serverStream(
        serviceCall: ColdDownstreamCallDescriptor<REQUEST, RESPONSE>,
        request: REQUEST
    ): Flow<RESPONSE> {
        val reference = nextCallReference()
        val pendingCall = ColdDownstreamPendingRPC.Client(connection, reference, serviceCall) {
            println("Client - ServerStream finished")
            clientPendingCalls.remove(reference)
        }
        clientPendingCalls[reference] = pendingCall

        return pendingCall.perform(request)


        // val streamChannel = Channel<RESPONSE>()
        // val openStream = object: OpenInStream<REQUEST, RESPONSE>(
        //     descriptor = serviceCall,
        //     payload = request,
        // ) {
        //     override suspend fun accept(data: RESPONSE): Boolean {
        //         streamChannel.send(data)
        //         return true
        //     }
        //
        //     override suspend fun dataEnd() {
        //         streamChannel.close()
        //     }
        //
        //     override suspend fun reject(throwable: Throwable) {
        //         streamChannel.close(throwable)
        //     }
        //
        //     override suspend fun close(throwable: Throwable?): Boolean {
        //         return true
        //     }
        // }
        //
        // run(openStream)
        //
        // emitAll(streamChannel)
    }

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> biStream(
        serviceCall: ColdBistreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>,
        request: REQUEST,
        clientStream: Flow<CLIENT_STREAM>
    ): Flow<RESPONSE> = flow {
        val reference = nextCallReference()
        val pendingCall = ColdBistreamPendingRPC.Client(connection, reference, serviceCall, clientStream) {
            clientPendingCalls.remove(reference)
        }
        clientPendingCalls[reference] = pendingCall

        emitAll(pendingCall.perform(request))


        // var streamingJob: Job? = null
        // val streamChannel = Channel<RESPONSE>()
        // val openStream = object: OpenInStream<REQUEST, RESPONSE>(serviceCall, request) {
        //     override suspend fun accept(data: RESPONSE): Boolean {
        //         streamChannel.send(data)
        //         return true
        //     }
        //
        //     override suspend fun dataEnd() {
        //         TODO("Not yet implemented")
        //     }
        //
        //     override suspend fun reject(throwable: Throwable) {
        //
        //         streamChannel.close(throwable)
        //     }
        //
        //     override suspend fun close(throwable: Throwable?): Boolean {
        //         val capturedStreamingJob = streamingJob
        //         if (capturedStreamingJob != null) {
        //             capturedStreamingJob.cancel(CancellationException("Server closed the connection.", throwable))
        //         } else {
        //             streamingJob = Job()
        //         }
        //         return true
        //     }
        // }
        //
        // val callReference = run(openStream)
        //
        // openStream.stateManager.await(PendingRPC.State.Ready)
        //
        // if (streamingJob == null) {
        //     streamingJob = subscribeOutStream(callReference, serviceCall, clientStream)
        // } else {
        //     error("streamingJob was set, probably from a different thread. That's illegal.")
        // }
        //
        // emitAll(streamChannel)
    }

    private suspend fun run(call: PendingRPC<*, *>): RPCReference {
        val reference = nextCallReference()
        val event = UpstreamRPCEvent.Open(call.descriptor.identifier)
        val frame = OutgoingRPCFrame(
            header = RPCFrame.Header(
                callReference = reference,
                event = event,
            ),
            serializationStrategy = call.payloadSerializationStrategy as SerializationStrategy<Any?>,
            data = call.payload,
        )

        pendingRequests[reference] = call as PendingRPC<Any, Any>

        connection.send(frame)

        return reference
    }

    // private fun <PAYLOAD, CLIENT_STREAM> subscribeOutStream(callReference: RPCReference, descriptor: LocalOutStreamCallDescriptor<PAYLOAD, CLIENT_STREAM>, clientStream: Flow<CLIENT_STREAM>): Job {
    //     return outStreamScope.launch {
    //         try {
    //             clientStream.collect {
    //                 println("Sending: $it")
    //                 connection.send(
    //                     OutgoingRPCFrame(
    //                         header = RPCFrame.Header(
    //                             callReference = callReference,
    //                             event = UpstreamRPCEvent.Data
    //                         ),
    //                         serializationStrategy = descriptor.clientStreamSerializer as SerializationStrategy<Any?>,
    //                         data = it
    //                     )
    //                 )
    //             }
    //
    //             connection.send(
    //                 OutgoingRPCFrame(
    //                     header = RPCFrame.Header(
    //                         callReference = callReference,
    //                         event = UpstreamRPCEvent.Close
    //                     ),
    //                     serializationStrategy = Unit.serializer() as SerializationStrategy<Any?>,
    //                     data = Unit
    //                 )
    //             )
    //         } catch (t: Throwable) {
    //             connection.send(
    //                 OutgoingRPCFrame(
    //                     header = RPCFrame.Header(
    //                         callReference = callReference,
    //                         event = UpstreamRPCEvent.Error,
    //                     ),
    //                     serializationStrategy = descriptor.errorSerializer as SerializationStrategy<Any?>,
    //                     data = t,
    //                 )
    //             )
    //         }
    //     }
    // }

    private fun nextCallReference(): Int {
        return callReferenceCounter++
    }

    @OptIn(InternalSerializationApi::class)
    private suspend fun <DATA: Any> send(header: RPCFrame.Header<UpstreamRPCEvent>, data: DATA) {
        return connection.send(OutgoingRPCFrame(header, data::class.serializer() as SerializationStrategy<Any?>, data))
    }

    @OptIn(InternalSerializationApi::class)
    private suspend fun send(header: RPCFrame.Header<UpstreamRPCEvent>) {
        return connection.send(OutgoingRPCFrame(header, Unit.serializer() as SerializationStrategy<Any?>, Unit))
    }

    @OptIn(InternalSerializationApi::class)
    private suspend fun sendUnknownReferenceError(callReference: RPCReference) {
        val error = UnknownRPCReferenceException(callReference)
        sendError(callReference, error)
    }

    @OptIn(InternalSerializationApi::class)
    private suspend inline fun <reified ERROR: RPCError> closeWithError(callReference: RPCReference, error: ERROR): Nothing {
        sendError(callReference, error)

        throw error
    }

    @OptIn(InternalSerializationApi::class)
    private suspend inline fun <reified ERROR: RPCError> sendError(callReference: RPCReference, error: ERROR) {
        connection.send(OutgoingRPCFrame(RPCFrame.Header(callReference, UpstreamRPCEvent.Error), ERROR::class.serializer() as SerializationStrategy<Any?>, error))
    }

    private class Stream(
        private val incomingChannel: Channel<Any?>,
        // private val outgoingChannel: Channel<Any?>,
        private val dataSerializer: DeserializationStrategy<Any?>,
        private val errorSerializer: RPCErrorSerializer
    ) {
        suspend fun accept(decoder: Decoder) {
            val data = decoder.decodeSerializableValue(dataSerializer)
            incomingChannel.send(data)
        }

        suspend fun dataEnd() {
            incomingChannel.close()
        }

        suspend fun reject(decoder: Decoder) {
            val throwable = decoder.decodeSerializableValue(errorSerializer)
            incomingChannel.close(throwable)
        }

        suspend fun close() {
            // outgoingChannel.close()
        }
    }

    class Factory(
        private val serviceRegistry: ServiceRegistry,
        private val outStreamScope: CoroutineScope,
        private val responseScope: CoroutineScope,
    ): RPCProtocol.Factory {
        override val version = RPCProtocol.Version.Ascension

        override fun create(connection: RPCConnection): AscensionRPCProtocol {
            return AscensionRPCProtocol(serviceRegistry, connection, outStreamScope, responseScope)
        }
    }
}
