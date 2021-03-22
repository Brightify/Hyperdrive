package org.brightify.hyperdrive.krpc.api.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.BaseRPCError
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.api.ServiceCallIdentifier

@Serializable
class RPCNotFoundError(
    val call: ServiceCallIdentifier,
): BaseRPCError(RPCError.StatusCode.NotFound, "Service call $call is not found. Either the service is not registered, or it has been renamed.")