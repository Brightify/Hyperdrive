package org.brightify.hyperdrive.krpc.client.impl.ktor

import io.ktor.http.cio.websocket.*
import org.brightify.hyperdrive.krpc.frame.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.frame.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.frame.RPCEvent
import org.brightify.hyperdrive.krpc.api.WebSocketFrameConverter

class SingleFrameConverterWrapper private constructor(
    private val delegatedRpcFrameToWebSocketFrame: (OutgoingRPCFrame<RPCEvent>) -> Frame,
    private val delegatedRpcFrameFromWebSocketFrame: (Frame) -> IncomingRPCFrame<RPCEvent>
): WebSocketFrameConverter<Frame> {

    override fun rpcFrameToWebSocketFrame(frame: OutgoingRPCFrame<RPCEvent>): Frame = delegatedRpcFrameToWebSocketFrame(frame)

    override fun rpcFrameFromWebSocketFrame(frame: Frame): IncomingRPCFrame<RPCEvent> = delegatedRpcFrameFromWebSocketFrame(frame)

    companion object {
        fun binary(binaryConverter: WebSocketFrameConverter<Frame.Binary>): SingleFrameConverterWrapper {
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

        fun text(textConverter: WebSocketFrameConverter<Frame.Text>): SingleFrameConverterWrapper {
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