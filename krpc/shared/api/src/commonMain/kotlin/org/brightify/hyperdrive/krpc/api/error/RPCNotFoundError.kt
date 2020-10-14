package org.brightify.hyperdrive.krpc.api.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.RPCError

@Serializable
open class RPCNotFoundError(override val debugMessage: String): RPCError() {
    override val statusCode: StatusCode = StatusCode.NotFound
}
