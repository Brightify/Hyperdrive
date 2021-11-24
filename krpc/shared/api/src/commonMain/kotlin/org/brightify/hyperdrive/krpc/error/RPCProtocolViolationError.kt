package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.RPCError

@Serializable
class RPCProtocolViolationError(override val debugMessage: String): RPCError {
    override val statusCode: RPCError.StatusCode = RPCError.StatusCode.ProtocolViolation
}
