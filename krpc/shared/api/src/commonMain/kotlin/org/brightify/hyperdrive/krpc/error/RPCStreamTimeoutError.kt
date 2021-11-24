package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.RPCError

@Serializable
class RPCStreamTimeoutError(
    val timeoutInMillis: Long,
): RPCError {
    override val statusCode: RPCError.StatusCode = RPCError.StatusCode.RequestTimeout
    override val debugMessage: String = "Stream not started in time. Timeout is ${timeoutInMillis}ms"
}