package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import org.brightify.hyperdrive.krpc.ServiceRegistry
import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.ClientCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdBistreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdDownstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdUpstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.DownstreamRPCEvent
import org.brightify.hyperdrive.krpc.api.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.api.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.api.RPCConnection
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.api.RPCEvent
import org.brightify.hyperdrive.krpc.api.RPCFrame
import org.brightify.hyperdrive.krpc.protocol.RPCProtocol
import org.brightify.hyperdrive.krpc.api.RPCReference
import org.brightify.hyperdrive.krpc.api.UnexpectedRPCEventException
import org.brightify.hyperdrive.krpc.api.UpstreamRPCEvent
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.api.error.RPCNotFoundError
import org.brightify.hyperdrive.krpc.api.error.UnknownRPCReferenceException

class AscensionRPCProtocol(
    private val serviceRegistry: ServiceRegistry,
    private val connection: RPCConnection,
): RPCProtocol {
    override val version = RPCProtocol.Version.Ascension

    override val isActive: Boolean
        get() = connection.isActive

    private val serverPendingCalls = mutableMapOf<RPCReference, PendingRPC.Server<*, *>>()
    private val baseRPCErrorSerializer = RPCErrorSerializer()

    private val clientPendingCalls = mutableMapOf<RPCReference, PendingRPC.Client<*, *, *>>()

    // TODO: Replace with AtomicInt
    private var callReferenceCounter: RPCReference = RPCReference.MIN_VALUE

    private val receivingJob: Job

    init {
        receivingJob = connection.launch {
            while (isActive) {
                val frame = connection.receive()
                @Suppress("UNUSED_VARIABLE")
                val exhaustive: Unit = when (val event = frame.header.event) {
                    is DownstreamRPCEvent -> handleDownstreamEvent(frame as IncomingRPCFrame<DownstreamRPCEvent>)
                    is UpstreamRPCEvent -> handleUpstreamEvent(frame as IncomingRPCFrame<UpstreamRPCEvent>)
                    else -> closeWithError(frame.header.callReference, UnexpectedRPCEventException(event::class))
                }
            }
        }
    }

    private suspend fun handleDownstreamEvent(frame: IncomingRPCFrame<DownstreamRPCEvent>) {
        val pendingCall = clientPendingCalls[frame.header.callReference] ?: return run {
            if (frame.header.event != DownstreamRPCEvent.Error) {
                sendUnknownReferenceError(frame.header.callReference)
            }
        }
        pendingCall.accept(frame)
    }

    private suspend fun handleUpstreamEvent(frame: IncomingRPCFrame<UpstreamRPCEvent>) {
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

        val event = frame.header.event
        val reference = frame.header.callReference
        val existingPendingCall = serverPendingCalls[reference]
        val pendingCall = when {
            existingPendingCall != null -> existingPendingCall
            event is UpstreamRPCEvent.Open -> {
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
                    null -> closeWithError(reference, RPCNotFoundError(event.serviceCall))
                }

                serverPendingCalls[reference] = newPendingCall
                newPendingCall
            }
            else -> {
                // We don't want to be stuck in a loop of sending the same error up and down.
                if (event != UpstreamRPCEvent.Error) {
                    sendUnknownReferenceError(frame.header.callReference)
                }
                return
            }
        }

        pendingCall.accept(frame)
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
    }

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> clientStream(serviceCall: ColdUpstreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>, request: REQUEST, clientStream: Flow<CLIENT_STREAM>): RESPONSE {
        val reference = nextCallReference()
        val pendingCall = ColdUpstreamPendingRPC.Client(connection, reference, serviceCall, clientStream) {
            clientPendingCalls.remove(reference)
        }
        clientPendingCalls[reference] = pendingCall

        return pendingCall.perform(request)
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
    }

    private fun nextCallReference(): Int {
        return callReferenceCounter++
    }

    private suspend fun sendUnknownReferenceError(callReference: RPCReference) {
        val error = UnknownRPCReferenceException(callReference)
        sendError(callReference, error)
    }

    private suspend inline fun <reified ERROR> closeWithError(callReference: RPCReference, error: ERROR): Nothing where ERROR: RPCError, ERROR: Throwable {
        sendError(callReference, error)

        throw error
    }

    private suspend inline fun <reified ERROR: RPCError> sendError(callReference: RPCReference, error: ERROR) {
        connection.send(OutgoingRPCFrame(RPCFrame.Header(callReference, UpstreamRPCEvent.Error), baseRPCErrorSerializer, error))
    }

    class Factory(
        private val serviceRegistry: ServiceRegistry,
    ): RPCProtocol.Factory {
        override val version = RPCProtocol.Version.Ascension

        override fun create(connection: RPCConnection): AscensionRPCProtocol {
            return AscensionRPCProtocol(serviceRegistry, connection)
        }
    }
}
