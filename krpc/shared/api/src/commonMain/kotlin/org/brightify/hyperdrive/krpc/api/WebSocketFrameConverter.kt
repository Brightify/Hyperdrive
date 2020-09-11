package org.brightify.hyperdrive.krpc.api

import io.ktor.http.cio.websocket.*

interface WebSocketFrameConverter<FRAME: Frame> {
    fun upstreamFrameToWebSocketFrame(frame: RPCFrame<RPCEvent.Upstream>): FRAME

    fun downstreamFrameToWebSocketFrame(frame: RPCFrame<RPCEvent.Downstream>): FRAME

    fun upstreamFrameFromWebSocketFrame(frame: FRAME, resolveCall: (RPCFrame.Header<RPCEvent.Upstream>) -> CallDescriptor): RPCFrame<RPCEvent.Upstream>

    fun downstreamFrameFromWebSocketFrame(frame: FRAME, resolveCall: (RPCFrame.Header<RPCEvent.Downstream>) -> CallDescriptor): RPCFrame<RPCEvent.Downstream>

    class UnsupportedFrameTypeException(
        val frameType: FrameType,
        val converter: WebSocketFrameConverter<*>
    ): RuntimeException("Frame type ${frameType.name} is not supported by converter $converter!")
}