package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier

@Serializable
class RPCNotFoundError(
    val call: ServiceCallIdentifier,
): RPCError {
    override val statusCode: RPCError.StatusCode = RPCError.StatusCode.NotFound
    override val debugMessage: String = "Service call $call is not found. Either the service is not registered, or it has been renamed."
}