package org.brightify.hyperdrive.krpc.impl

import kotlinx.serialization.protobuf.ProtoBufBuilder
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.application.PayloadSerializer
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer

public class ProtobufCombinedSerializer(builder: ProtoBufBuilder.() -> Unit): SerializerRegistry.CombinedSerializer {
    override val transportFrameSerializer: TransportFrameSerializer = ProtobufTransportFrameSerializer(builder)
    override val payloadSerializer: PayloadSerializer = ProtobufPayloadSerializer(builder)

    public class Factory(private val builder: ProtoBufBuilder.() -> Unit = { }): SerializerRegistry.CombinedSerializer.Factory {
        override val format: SerializationFormat = SerializationFormat.Binary.Protobuf

        override fun create(): SerializerRegistry.CombinedSerializer {
            return ProtobufCombinedSerializer(builder)
        }
    }
}