package org.brightify.hyperdrive.krpc.api.error

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.api.RPCReference

@Serializable
class UnknownRPCReferenceException private constructor(override val debugMessage: String): RPCError() {
    override val statusCode = StatusCode.BadRequest

    constructor(reference: RPCReference): this("Unknown RPC reference <$reference>!")
}