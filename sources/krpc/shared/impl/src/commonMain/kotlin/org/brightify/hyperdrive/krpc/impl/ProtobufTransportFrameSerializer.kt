package org.brightify.hyperdrive.krpc.impl

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoBufBuilder
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.SerializedFrame
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer

public class ProtobufTransportFrameSerializer(private val builder: ProtoBufBuilder.() -> Unit = { }): TransportFrameSerializer {
    private val protobuf = ProtoBuf {
        encodeDefaults = false
        builder()
    }

    override val format: SerializationFormat = SerializationFormat.Binary.Protobuf

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

