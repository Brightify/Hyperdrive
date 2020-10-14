package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.Serializable

@Serializable
abstract class RPCError: Throwable() {
    abstract val statusCode: StatusCode
    abstract val debugMessage: String

    @Serializable
    enum class StatusCode(val code: Int) {
        Continue(100),
        SwitchingProtocol(101),
        Processing(102),
        EarlyHints(103),

        BadRequest(400),
        Unauthorized(401),
        NotFound(404),
        InternalServerError(500),
    }
}