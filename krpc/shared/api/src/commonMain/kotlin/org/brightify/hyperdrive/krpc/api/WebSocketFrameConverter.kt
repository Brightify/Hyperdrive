package org.brightify.hyperdrive.krpc.api

import io.ktor.http.cio.websocket.*

interface WebSocketFrameConverter<FRAME: Frame, OUTGOING: RPCEvent, INCOMING: RPCEvent> {
    fun rpcFrameToWebSocketFrame(frame: OutgoingRPCFrame<OUTGOING>): FRAME

    fun rpcFrameFromWebSocketFrame(frame: FRAME): IncomingRPCFrame<INCOMING>

    class UnsupportedFrameTypeException(
        val frameType: FrameType,
        val converter: WebSocketFrameConverter<*, *, *>
    ): RuntimeException("Frame type ${frameType.name} is not supported by converter $converter!")
}