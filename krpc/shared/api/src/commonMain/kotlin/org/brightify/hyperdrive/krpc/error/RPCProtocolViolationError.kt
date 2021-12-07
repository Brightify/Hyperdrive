package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.RPCError

@Serializable
public class RPCProtocolViolationError(override val debugMessage: String): Throwable(debugMessage), RPCError {
    override val statusCode: RPCError.StatusCode = RPCError.StatusCode.ProtocolViolation
}
