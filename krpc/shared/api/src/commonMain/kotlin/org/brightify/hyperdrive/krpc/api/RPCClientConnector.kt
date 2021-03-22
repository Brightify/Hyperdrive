package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

interface RPCClientConnector {
    suspend fun withConnection(block: suspend RPCConnection.() -> Unit)

}

suspend inline fun <reified SPECIFIC_INCOMING: RPCEvent> RPCConnection.receiveSpecific(): IncomingRPCFrame<SPECIFIC_INCOMING> {
    val receivedFrame = receive()
    if (receivedFrame.header.event is SPECIFIC_INCOMING) {
        return receivedFrame as IncomingRPCFrame<SPECIFIC_INCOMING>
    } else {
        throw UnexpectedRPCEventException(SPECIFIC_INCOMING::class, receivedFrame.header.event.let { it::class })
    }
}

@Serializable
class UnexpectedRPCEventException: InternalRPCError {
    constructor(expectedEvent: KClass<out RPCEvent>, actualEvent: KClass<out RPCEvent>): super(RPCError.StatusCode.ProtocolViolation,"Unexpected RPC event! Expected <${expectedEvent.simpleName}>, got <${actualEvent.simpleName}>.")

    constructor(actualEvent: KClass<out RPCEvent>, reason: String? = null): super(RPCError.StatusCode.ProtocolViolation, "Unexpected RPC event <${actualEvent.simpleName}>, reason: $reason.")
}