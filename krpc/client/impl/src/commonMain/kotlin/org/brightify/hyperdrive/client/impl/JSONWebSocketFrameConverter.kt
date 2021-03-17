package org.brightify.hyperdrive.client.impl

import io.ktor.http.cio.websocket.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus
import org.brightify.hyperdrive.krpc.api.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.api.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.api.RPCEvent
import org.brightify.hyperdrive.krpc.api.RPCFrameDeserializationStrategy
import org.brightify.hyperdrive.krpc.api.RPCFrameSerializationStrategy
import org.brightify.hyperdrive.krpc.api.WebSocketFrameConverter

class JSONWebSocketFrameConverter<OUTGOING: RPCEvent, INCOMING: RPCEvent>(
    private val outgoingSerializer: RPCFrameSerializationStrategy<OUTGOING>,
    private val incomingDeserializer: RPCFrameDeserializationStrategy<INCOMING>
): WebSocketFrameConverter<Frame.Text, OUTGOING, INCOMING> {

    private val format = Json {
        serializersModule += RPCEvent.serializersModule
    }

    override fun rpcFrameToWebSocketFrame(frame: OutgoingRPCFrame<OUTGOING>): Frame.Text {
        return Frame.Text(format.encodeToString(outgoingSerializer, frame))
    }

    override fun rpcFrameFromWebSocketFrame(frame: Frame.Text): IncomingRPCFrame<INCOMING> {
        return format.decodeFromString(incomingDeserializer, frame.readText())
    }

}