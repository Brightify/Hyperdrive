package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.RPCError

@Serializable
class RPCStreamTimeoutError(
    val timeoutInMillis: Long,
): Throwable(message(timeoutInMillis)), RPCError {
    override val statusCode: RPCError.StatusCode = RPCError.StatusCode.RequestTimeout
    override val debugMessage: String = message(timeoutInMillis)

    companion object {
        fun message(timeoutInMillis: Long): String = "Stream not started in time. Timeout is ${timeoutInMillis}ms"
    }
}
