package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.InternalRPCError
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.frame.RPCEvent
import kotlin.reflect.KClass

@Serializable
class UnexpectedRPCEventException: InternalRPCError {
    constructor(expectedEvent: KClass<out RPCEvent>, actualEvent: KClass<out RPCEvent>): super(RPCError.StatusCode.ProtocolViolation,"Unexpected RPC event! Expected <${expectedEvent.simpleName}>, got <${actualEvent.simpleName}>.")

    constructor(actualEvent: KClass<out RPCEvent>, reason: String? = null): super(RPCError.StatusCode.ProtocolViolation, "Unexpected RPC event <${actualEvent.simpleName}>, reason: $reason.")
}