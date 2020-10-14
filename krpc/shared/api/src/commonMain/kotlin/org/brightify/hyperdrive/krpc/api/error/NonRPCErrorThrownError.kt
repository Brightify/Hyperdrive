package org.brightify.hyperdrive.krpc.api.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.RPCError

@Serializable
class NonRPCErrorThrownError(override val debugMessage: String): RPCError() {
    constructor(throwable: Throwable): this("NonRPC Error Thrown: ${throwable.message} - $throwable")

    override val statusCode = StatusCode.InternalServerError
}
