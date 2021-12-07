package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.RPCError

@Serializable
public class InternalServerError private constructor(
    override val statusCode: RPCError.StatusCode,
    override val debugMessage: String,
    public val stacktrace: String,
): Throwable(debugMessage), RPCError {
    public constructor(throwable: Throwable): this(
        RPCError.StatusCode.InternalServerError,
        "Unregistered Error Thrown: ${throwable.message} - $throwable",
        throwable.stackTraceToString(),
    )
}

