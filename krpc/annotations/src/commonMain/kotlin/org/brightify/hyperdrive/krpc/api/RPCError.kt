package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.Serializable

@Serializable
abstract class RPCError : Throwable() {

    abstract val statusCode: StatusCode
    abstract val debugMessage: String

    override fun toString(): String {
        return super.toString() + "#status = $statusCode & message = $debugMessage"
    }

    @Serializable
    enum class StatusCode(val code: Int) {

        Continue(100),
        SwitchingProtocol(101),
        Processing(102),
        EarlyHints(103),

        BadRequest(400),
        Unauthorized(401),
        Forbidden(403),
        NotFound(404),
        Gone(410),
        UnsupportedMediaType(415),
        ImATeapot(418),
        UnprocessableEntity(422),
        FailedDependency(424),
        UpgradeRequired(426),
        TooManyRequests(429),

        InternalServerError(500),
        NotImplemented(501),
    }
}
