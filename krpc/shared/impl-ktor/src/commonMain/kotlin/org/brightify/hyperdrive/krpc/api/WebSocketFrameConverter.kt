package org.brightify.hyperdrive.krpc.api

import io.ktor.http.cio.websocket.*
import org.brightify.hyperdrive.krpc.frame.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.frame.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.frame.RPCEvent

interface WebSocketFrameConverter<FRAME: Frame> {
    fun rpcFrameToWebSocketFrame(frame: OutgoingRPCFrame<RPCEvent>): FRAME

    fun rpcFrameFromWebSocketFrame(frame: FRAME): IncomingRPCFrame<RPCEvent>

    class UnsupportedFrameTypeException(
        val frameType: FrameType,
        val converter: WebSocketFrameConverter<*>
    ): RuntimeException("Frame type ${frameType.name} is not supported by converter $converter!")
}