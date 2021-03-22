package org.brightify.hyperdrive.krpc.api.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.BaseRPCError
import org.brightify.hyperdrive.krpc.api.Error
import org.brightify.hyperdrive.krpc.api.RPCError

@Serializable
class InternalServerError: BaseRPCError {
    constructor(throwable: Throwable): super(RPCError.StatusCode.InternalServerError, "Unregistered Error Thrown: ${throwable.message} - $throwable")
}