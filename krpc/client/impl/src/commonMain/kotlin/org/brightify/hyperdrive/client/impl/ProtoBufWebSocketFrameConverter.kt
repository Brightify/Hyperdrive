package org.brightify.hyperdrive.client.impl

import io.ktor.http.cio.websocket.*
import kotlinx.serialization.modules.plus
import kotlinx.serialization.protobuf.ProtoBuf
import org.brightify.hyperdrive.krpc.api.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.api.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.api.RPCEvent
import org.brightify.hyperdrive.krpc.api.RPCFrameDeserializationStrategy
import org.brightify.hyperdrive.krpc.api.RPCFrameSerializationStrategy
import org.brightify.hyperdrive.krpc.api.WebSocketFrameConverter

class ProtoBufWebSocketFrameConverter<OUTGOING: RPCEvent, INCOMING: RPCEvent>(
    private val outgoingSerializer: RPCFrameSerializationStrategy<OUTGOING>,
    private val incomingDeserializer: RPCFrameDeserializationStrategy<INCOMING>
): WebSocketFrameConverter<Frame.Binary, OUTGOING, INCOMING> {

    private val format = ProtoBuf {
        encodeDefaults = false
        serializersModule += RPCEvent.serializersModule
    }

    override fun rpcFrameToWebSocketFrame(frame: OutgoingRPCFrame<OUTGOING>): Frame.Binary {
        return Frame.Binary(true, format.encodeToByteArray(outgoingSerializer, frame))
    }

    override fun rpcFrameFromWebSocketFrame(frame: Frame.Binary): IncomingRPCFrame<INCOMING> {
        return format.decodeFromByteArray(incomingDeserializer, frame.readBytes())
    }

}