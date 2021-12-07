package org.brightify.hyperdrive.krpc.impl

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.application.PayloadSerializer

public class JsonPayloadSerializer(private val builder: JsonBuilder.() -> Unit = { }): PayloadSerializer {
    private val json = Json {
        encodeDefaults = false
        builder()
    }

    override val format: SerializationFormat = SerializationFormat.Text.Json

    override fun <T> serialize(strategy: SerializationStrategy<T>, payload: T): SerializedPayload {
        return SerializedPayload.Text(
            json.encodeToString(strategy, payload),
            SerializationFormat.Text.Json
        )
    }

    override fun <T> deserialize(strategy: DeserializationStrategy<T>, payload: SerializedPayload): T {
        require(payload.format == SerializationFormat.Text.Json)
        return when (payload) {
            is SerializedPayload.Binary -> json.decodeFromString(strategy, payload.binary.decodeToString())
            is SerializedPayload.Text -> json.decodeFromString(strategy, payload.text)
        }
    }
}