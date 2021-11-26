package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.InternalRPCError
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.util.RPCReference

@Serializable
class UnknownRPCReferenceException(
    val reference: RPCReference
): Throwable(message(reference)), RPCError {
    override val statusCode: RPCError.StatusCode = RPCError.StatusCode.ProtocolViolation
    override val debugMessage: String = message(reference)

    companion object {
        fun message(reference: RPCReference): String {
            return "Unknown RPC reference <$reference>!"
        }
    }
}