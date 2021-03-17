package org.brightify.hyperdrive.krpc.api.error

import org.brightify.hyperdrive.krpc.api.RPCError

class UnrecognizedRPCError(
    override val statusCode: StatusCode,
    override val debugMessage: String,
    val errorType: String,
): RPCError() {

}

class RPCProtocolViolationError(
    override val debugMessage: String,
): RPCError() {
    override val statusCode: StatusCode = StatusCode.BadRequest
}

class RPCStreamTimeoutError(
    override val debugMessage: String,
    val timeoutInMillis: Long,
): RPCError() {
    override val statusCode: StatusCode = StatusCode.RequestTimeout
}