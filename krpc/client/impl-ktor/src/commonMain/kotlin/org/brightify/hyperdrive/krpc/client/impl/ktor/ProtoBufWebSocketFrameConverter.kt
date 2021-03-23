package org.brightify.hyperdrive.krpc.client.impl.ktor

import io.ktor.http.cio.websocket.*
import kotlinx.serialization.modules.plus
import kotlinx.serialization.protobuf.ProtoBuf
import org.brightify.hyperdrive.krpc.frame.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.frame.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.frame.RPCEvent
import org.brightify.hyperdrive.krpc.frame.serialization.RPCFrameDeserializationStrategy
import org.brightify.hyperdrive.krpc.frame.serialization.RPCFrameSerializationStrategy
import org.brightify.hyperdrive.krpc.api.WebSocketFrameConverter

class ProtoBufWebSocketFrameConverter(
    private val outgoingSerializer: RPCFrameSerializationStrategy<RPCEvent>,
    private val incomingDeserializer: RPCFrameDeserializationStrategy<RPCEvent>
): WebSocketFrameConverter<Frame.Binary> {

    private val format = ProtoBuf {
        encodeDefaults = false
        serializersModule += RPCEvent.serializersModule
    }

    override fun rpcFrameToWebSocketFrame(frame: OutgoingRPCFrame<RPCEvent>): Frame.Binary {
        return Frame.Binary(true, format.encodeToByteArray(outgoingSerializer, frame))
    }

    override fun rpcFrameFromWebSocketFrame(frame: Frame.Binary): IncomingRPCFrame<RPCEvent> {
        return format.decodeFromByteArray(incomingDeserializer, frame.readBytes())
    }

}