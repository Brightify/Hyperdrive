package org.brightify.hyperdrive.krpc.error

import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.api.RPCErrorException

@Deprecated("Use asRPCError method instead", ReplaceWith("asRPCError()"))
public fun Throwable.RPCError(): RPCError {
    return asRPCError()
}

public fun Throwable.asRPCError(): RPCError {
    return when (this) {
        is RPCError -> this
        is RPCErrorException -> error
        else -> InternalServerError(this)
    }
}