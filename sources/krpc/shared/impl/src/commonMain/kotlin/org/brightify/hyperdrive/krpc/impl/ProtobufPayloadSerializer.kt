package org.brightify.hyperdrive.krpc.impl

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoBufBuilder
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.application.PayloadSerializer

public class ProtobufPayloadSerializer(private val builder: ProtoBufBuilder.() -> Unit = { }): PayloadSerializer {
    private val protobuf = ProtoBuf {
        encodeDefaults = false
        builder()
    }

    override val format: SerializationFormat = SerializationFormat.Binary.Protobuf

    override fun <T> serialize(strategy: SerializationStrategy<T>, payload: T): SerializedPayload {
        return SerializedPayload.Binary(
            protobuf.encodeToByteArray(strategy, payload),
            SerializationFormat.Binary.Protobuf
        )
    }

    override fun <T> deserialize(strategy: DeserializationStrategy<T>, payload: SerializedPayload): T {
        require(payload.format == SerializationFormat.Binary.Protobuf)
        return when (payload) {
            is SerializedPayload.Binary -> protobuf.decodeFromByteArray(strategy, payload.binary)
            is SerializedPayload.Text -> protobuf.decodeFromHexString(strategy, payload.text)
        }
    }
}