package org.brightify.hyperdrive.krpc.api.error

import org.brightify.hyperdrive.krpc.api.RPCError

class UnrecognizedRPCError(
    override val statusCode: StatusCode,
    override val debugMessage: String,
    val errorType: String,
): RPCError() {

}