package org.brightify.hyperdrive.krpc.api.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.BaseRPCError
import org.brightify.hyperdrive.krpc.api.InternalRPCError
import org.brightify.hyperdrive.krpc.api.RPCError

@Serializable
class UnrecognizedRPCError(
    override val statusCode: RPCError.StatusCode,
    override val debugMessage: String,
    val errorType: String,
): Throwable(), RPCError

@Serializable
class RPCProtocolViolationError: InternalRPCError {
    constructor(debugMessage: String): super(RPCError.StatusCode.ProtocolViolation, debugMessage)
}

@Serializable
class RPCStreamTimeoutError(
    val timeoutInMillis: Long,
): BaseRPCError(RPCError.StatusCode.RequestTimeout, "Stream not started in time. Timeout is ${timeoutInMillis}ms")