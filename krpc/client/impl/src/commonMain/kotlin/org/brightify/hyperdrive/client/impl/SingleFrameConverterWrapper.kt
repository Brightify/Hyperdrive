package org.brightify.hyperdrive.client.impl

import io.ktor.http.cio.websocket.*
import org.brightify.hyperdrive.krpc.api.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.api.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.api.RPCEvent
import org.brightify.hyperdrive.krpc.api.WebSocketFrameConverter

class SingleFrameConverterWrapper<OUTGOING: RPCEvent, INCOMING: RPCEvent> private constructor(
    private val delegatedRpcFrameToWebSocketFrame: (OutgoingRPCFrame<OUTGOING>) -> Frame,
    private val delegatedRpcFrameFromWebSocketFrame: (Frame) -> IncomingRPCFrame<INCOMING>
): WebSocketFrameConverter<Frame, OUTGOING, INCOMING> {

    override fun rpcFrameToWebSocketFrame(frame: OutgoingRPCFrame<OUTGOING>): Frame = delegatedRpcFrameToWebSocketFrame(frame)

    override fun rpcFrameFromWebSocketFrame(frame: Frame): IncomingRPCFrame<INCOMING> = delegatedRpcFrameFromWebSocketFrame(frame)

    companion object {
        fun <OUTGOING: RPCEvent, INCOMING: RPCEvent> binary(binaryConverter: WebSocketFrameConverter<Frame.Binary, OUTGOING, INCOMING>): SingleFrameConverterWrapper<OUTGOING, INCOMING> {
            return SingleFrameConverterWrapper(
                delegatedRpcFrameToWebSocketFrame = binaryConverter::rpcFrameToWebSocketFrame,
                delegatedRpcFrameFromWebSocketFrame = { frame ->
                    when (frame) {
                        is Frame.Binary -> binaryConverter.rpcFrameFromWebSocketFrame(frame)
                        else -> throw WebSocketFrameConverter.UnsupportedFrameTypeException(frame.frameType, binaryConverter)
                    }
                }
            )
        }

        fun <OUTGOING: RPCEvent, INCOMING: RPCEvent> text(textConverter: WebSocketFrameConverter<Frame.Text, OUTGOING, INCOMING>): SingleFrameConverterWrapper<OUTGOING, INCOMING> {
            return SingleFrameConverterWrapper(
                delegatedRpcFrameToWebSocketFrame = textConverter::rpcFrameToWebSocketFrame,
                delegatedRpcFrameFromWebSocketFrame = { frame ->
                    when (frame) {
                        is Frame.Text -> textConverter.rpcFrameFromWebSocketFrame(frame)
                        else -> throw WebSocketFrameConverter.UnsupportedFrameTypeException(frame.frameType, textConverter)
                    }
                }
            )
        }
    }
}