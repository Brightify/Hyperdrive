package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.RPCError

@Serializable
class UnrecognizedRPCError(
    override val statusCode: RPCError.StatusCode,
    override val debugMessage: String,
    val errorType: String,
): Throwable(message(statusCode, debugMessage, errorType)), RPCError {
    companion object {
        fun message(statusCode: RPCError.StatusCode, debugMessage: String, errorType: String): String {
            return "[${statusCode.code}] Unrecognized RPC error: ${errorType}. $debugMessage"
        }
    }
}

