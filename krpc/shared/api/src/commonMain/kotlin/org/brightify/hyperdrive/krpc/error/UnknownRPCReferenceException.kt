package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.InternalRPCError
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.util.RPCReference

@Serializable
class UnknownRPCReferenceException(
    val reference: RPCReference
): InternalRPCError(RPCError.StatusCode.ProtocolViolation, "Unknown RPC reference <$reference>!")