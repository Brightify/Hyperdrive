package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.api.RPCErrorException

@Serializable
class InternalServerError private constructor(
    override val statusCode: RPCError.StatusCode,
    override val debugMessage: String,
    val stacktrace: String,
): Throwable(debugMessage), RPCError {
    constructor(throwable: Throwable): this(
        RPCError.StatusCode.InternalServerError,
        "Unregistered Error Thrown: ${throwable.message} - $throwable",
        throwable.stackTraceToString(),
    )
}

@Deprecated("Use asRPCError method instead", ReplaceWith("asRPCError()"))
fun Throwable.RPCError(): RPCError {
    return asRPCError()
}

fun Throwable.asRPCError(): RPCError {
    return when (this) {
        is RPCError -> this
        is RPCErrorException -> error
        else -> InternalServerError(this)
    }
}