package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.BaseRPCError
import org.brightify.hyperdrive.krpc.api.RPCError

@Serializable
class RPCStreamTimeoutError(
    val timeoutInMillis: Long,
): BaseRPCError(RPCError.StatusCode.RequestTimeout, "Stream not started in time. Timeout is ${timeoutInMillis}ms")