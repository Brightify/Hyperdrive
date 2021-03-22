package org.brightify.hyperdrive.krpc.api.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.BaseRPCError
import org.brightify.hyperdrive.krpc.api.InternalRPCError
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.api.RPCReference

@Serializable
class UnknownRPCReferenceException(
    val reference: RPCReference
): InternalRPCError(RPCError.StatusCode.ProtocolViolation, "Unknown RPC reference <$reference>!")