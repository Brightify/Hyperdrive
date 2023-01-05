package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.RPCError

@Serializable
public class RPCStreamTimeoutError(
    public val timeoutInMillis: Long,
): Throwable(message(timeoutInMillis)), RPCError {
    override val statusCode: RPCError.StatusCode = RPCError.StatusCode.RequestTimeout
    override val debugMessage: String = message(timeoutInMillis)

    public companion object {
        public fun message(timeoutInMillis: Long): String = "Stream not started in time. Timeout is ${timeoutInMillis}ms"
    }
}
