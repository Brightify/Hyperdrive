package org.brightify.hyperdrive.krpc.api

import io.ktor.http.cio.websocket.*

interface WebSocketFrameConverter<FRAME: Frame> {
    fun rpcFrameToWebSocketFrame(frame: OutgoingRPCFrame<RPCEvent>): FRAME

    fun rpcFrameFromWebSocketFrame(frame: FRAME): IncomingRPCFrame<RPCEvent>

    class UnsupportedFrameTypeException(
        val frameType: FrameType,
        val converter: WebSocketFrameConverter<*>
    ): RuntimeException("Frame type ${frameType.name} is not supported by converter $converter!")
}