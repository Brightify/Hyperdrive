package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.RPCError

@Serializable
public class RPCProtocolViolationError(override val debugMessage: String): Throwable("$debugMessage Please report this bug to the Hyperdrive team."), RPCError {
    override val statusCode: RPCError.StatusCode = RPCError.StatusCode.ProtocolViolation
}
