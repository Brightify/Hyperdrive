package org.brightify.hyperdrive.krpc.impl

import kotlinx.serialization.json.JsonBuilder
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.application.PayloadSerializer
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer

public class JsonCombinedSerializer(builder: JsonBuilder.() -> Unit): SerializerRegistry.CombinedSerializer {
    override val transportFrameSerializer: TransportFrameSerializer = JsonTransportFrameSerializer(builder)
    override val payloadSerializer: PayloadSerializer = JsonPayloadSerializer(builder)

    public class Factory(private val builder: JsonBuilder.() -> Unit = { }): SerializerRegistry.CombinedSerializer.Factory {
        override val format: SerializationFormat = SerializationFormat.Text.Json

        override fun create(): SerializerRegistry.CombinedSerializer {
            return JsonCombinedSerializer(builder)
        }
    }
}