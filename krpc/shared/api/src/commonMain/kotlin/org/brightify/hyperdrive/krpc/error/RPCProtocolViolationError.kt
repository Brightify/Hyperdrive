package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.InternalRPCError
import org.brightify.hyperdrive.krpc.api.RPCError

@Serializable
class RPCProtocolViolationError: InternalRPCError {
    constructor(debugMessage: String): super(RPCError.StatusCode.ProtocolViolation, debugMessage)
}