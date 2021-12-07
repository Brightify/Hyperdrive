package org.brightify.hyperdrive.krpc.impl

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.SerializedFrame
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer

public class JsonTransportFrameSerializer(private val builder: JsonBuilder.() -> Unit = { }): TransportFrameSerializer {
    private val json = Json {
        encodeDefaults = false
        builder()
    }
    override val format: SerializationFormat = SerializationFormat.Text.Json

    override fun <T> serialize(strategy: SerializationStrategy<T>, frame: T): SerializedFrame {
        return SerializedFrame.Text(
            json.encodeToString(strategy, frame)
        )
    }

    override fun <T> deserialize(strategy: DeserializationStrategy<T>, frame: SerializedFrame): T = when (frame) {
        is SerializedFrame.Binary -> json.decodeFromString(strategy, frame.binary.decodeToString())
        is SerializedFrame.Text -> json.decodeFromString(strategy, frame.text)
    }
}

