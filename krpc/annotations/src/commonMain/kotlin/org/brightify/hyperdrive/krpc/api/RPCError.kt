package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.Serializable

public class RPCErrorException(public val error: RPCError): Throwable(error.debugMessage)

public interface RPCError {
    public val statusCode: StatusCode
    public val debugMessage: String

    @Serializable
    public enum class StatusCode(public val code: Int) {

        Continue(100),
        SwitchingProtocol(101),
        Processing(102),
        EarlyHints(103),

        BadRequest(400),
        Unauthorized(401),
        PaymentRequired(402),
        Forbidden(403),
        NotFound(404),
        MethodNotAllowed(405),
        NotAcceptable(406),
        ProxyAuthenticationRequired(407),
        RequestTimeout(408),
        Conflict(409),
        Gone(410),
        LengthRequired(411),
        PreconditionFailed(412),
        PayloadTooLarge(413),
        RequestURITooLong(414),
        UnsupportedMediaType(415),
        RequestedRangeNotSatisfiable(416),
        ExpectationFailed(417),
        ImATeapot(418),
        MisdirectedRequest(421),
        UnprocessableEntity(422),
        Locked(423),
        FailedDependency(424),
        UpgradeRequired(426),
        PreconditionRequired(428),
        TooManyRequests(429),
        RequestHeaderFieldsTooLarge(431),
        ConnectionClosedWithoutResponse(444),
        UnavailableForLegalReasons(451),
        ClientClosedRequest(499),

        InternalServerError(500),
        NotImplemented(501),
        BadGateway(502),
        ServiceUnavailable(503),
        GatewayTimeout(504),
        HTTPVersionNotSupported(505),
        VariantAlsoNegotiates(506),
        InsufficientStorage(507),
        LoopDetected(508),
        NotExtended(510),
        NetworkAuthenticationRequired(511),
        NetworkConnectTimeoutError(599),

        // kRPC Protocol Errors
        ProtocolViolation(600)
    }
}

public fun RPCError.throwable(): Throwable {
    return this as? Throwable ?: RPCErrorException(this)
}

@Serializable
public abstract class BaseRPCError(
    override val statusCode: RPCError.StatusCode,
    override val debugMessage: String
): Throwable(debugMessage), RPCError {
    override fun toString(): String {
        return super.toString() + "#status = $statusCode & message = $debugMessage"
    }
}

@Serializable
public class InternalRPCError(
    override val statusCode: RPCError.StatusCode,
    override val debugMessage: String,
): Throwable(debugMessage), RPCError {
    override fun toString(): String {
        return "Internal kRPC error. Please report this along with a reproducer. Status code: $statusCode. Debug message: $debugMessage. Super: ${super.toString()}"
    }
}
