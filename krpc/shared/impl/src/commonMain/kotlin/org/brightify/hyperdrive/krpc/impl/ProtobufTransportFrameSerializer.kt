package org.brightify.hyperdrive.krpc.impl

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoBufBuilder
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.SerializedFrame
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.protocol.ascension.PayloadSerializer
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer

class ProtobufCombinedSerializer(builder: ProtoBufBuilder.() -> Unit): SerializerRegistry.CombinedSerializer {
    override val transportFrameSerializer = ProtobufTransportFrameSerializer(builder)
    override val payloadSerializer = ProtobufPayloadSerializer(builder)

    class Factory(private val builder: ProtoBufBuilder.() -> Unit = { }): SerializerRegistry.CombinedSerializer.Factory {
        override val format: SerializationFormat = SerializationFormat.Binary.Protobuf

        override fun create(): SerializerRegistry.CombinedSerializer {
            return ProtobufCombinedSerializer(builder)
        }
    }
}

class ProtobufTransportFrameSerializer(private val builder: ProtoBufBuilder.() -> Unit = { }): TransportFrameSerializer {
    private val protobuf = ProtoBuf {
        encodeDefaults = false
        builder()
    }

    override val format = SerializationFormat.Binary.Protobuf

    override fun <T> serialize(strategy: SerializationStrategy<T>, frame: T): SerializedFrame {
        return SerializedFrame.Binary(
            protobuf.encodeToByteArray(strategy, frame)
        )
    }
    override fun <T> deserialize(strategy: DeserializationStrategy<T>, frame: SerializedFrame): T = when (frame) {
        is SerializedFrame.Binary -> protobuf.decodeFromByteArray(strategy, frame.binary)
        is SerializedFrame.Text -> protobuf.decodeFromHexString(strategy, frame.text)
    }
}

class ProtobufPayloadSerializer(private val builder: ProtoBufBuilder.() -> Unit = { }): PayloadSerializer {
    private val protobuf = ProtoBuf {
        encodeDefaults = false
        builder()
    }
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