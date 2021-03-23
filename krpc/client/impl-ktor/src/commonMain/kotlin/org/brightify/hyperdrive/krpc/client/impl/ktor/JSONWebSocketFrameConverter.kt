package org.brightify.hyperdrive.krpc.client.impl.ktor

import io.ktor.http.cio.websocket.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus
import org.brightify.hyperdrive.krpc.frame.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.frame.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.frame.RPCEvent
import org.brightify.hyperdrive.krpc.frame.serialization.RPCFrameDeserializationStrategy
import org.brightify.hyperdrive.krpc.frame.serialization.RPCFrameSerializationStrategy

class JSONWebSocketFrameConverter(
    private val outgoingSerializer: RPCFrameSerializationStrategy<RPCEvent>,
    private val incomingDeserializer: RPCFrameDeserializationStrategy<RPCEvent>
): org.brightify.hyperdrive.krpc.api.WebSocketFrameConverter<Frame.Text> {

    private val format = Json {
        encodeDefaults = false
        serializersModule += RPCEvent.serializersModule
    }

    override fun rpcFrameToWebSocketFrame(frame: OutgoingRPCFrame<RPCEvent>): Frame.Text {
        return Frame.Text(format.encodeToString(outgoingSerializer, frame))
    }

    override fun rpcFrameFromWebSocketFrame(frame: Frame.Text): IncomingRPCFrame<RPCEvent> {
        return format.decodeFromString(incomingDeserializer, frame.readText())
    }

}