package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier

@Serializable
public class RPCNotFoundError(
    public val call: ServiceCallIdentifier,
): Throwable(message(call)), RPCError {
    override val statusCode: RPCError.StatusCode = RPCError.StatusCode.NotFound
    override val debugMessage: String = message(call)

    public companion object {
        public fun message(call: ServiceCallIdentifier): String {
            return "Service call $call is not found. Either the service is not registered, or it has been renamed."
        }
    }
}