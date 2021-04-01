package org.brightify.hyperdrive.krpc.impl

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.SerializedFrame
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.protocol.ascension.PayloadSerializer
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer

class SerializerRegistry(
    private val combinedSerializerFactories: List<CombinedSerializer.Factory>,
) {
    constructor(vararg combinedSerializerFactories: CombinedSerializer.Factory): this(combinedSerializerFactories.toList())

    init {
        require(combinedSerializerFactories.toSet().count() == combinedSerializerFactories.count()) {
            "Only a single serializer for each format is supported!"
        }
    }

    private val supportedSerializationFormats = combinedSerializerFactories.map { it.format }
    private val combinedSerializers = mutableMapOf<SerializationFormat, CombinedSerializer>()

    val transportFrameSerializerFactory: TransportFrameSerializer.Factory = object: TransportFrameSerializer.Factory {
        override val supportedSerializationFormats: List<SerializationFormat>
            get() = this@SerializerRegistry.supportedSerializationFormats

        override fun create(format: SerializationFormat): TransportFrameSerializer {
            require(supportedSerializationFormats.contains(format)) { "Format $format not supported." }

            return combinedSerializers.getOrPut(format) {
                combinedSerializerFactories.single { it.format == format }.create()
            }.transportFrameSerializer
        }
    }

    val payloadSerializerFactory: PayloadSerializer.Factory = object: PayloadSerializer.Factory {
        override val supportedSerializationFormats = this@SerializerRegistry.supportedSerializationFormats

        override fun create(format: SerializationFormat): PayloadSerializer {
            require(supportedSerializationFormats.contains(format)) { "Format $format not supported." }

            return combinedSerializers.getOrPut(format) {
                combinedSerializerFactories.single { it.format == format }.create()
            }.payloadSerializer
        }

        override fun <T> deserialize(strategy: DeserializationStrategy<T>, payload: SerializedPayload): T {
            val serializer = create(payload.format)
            return serializer.deserialize(strategy, payload)
        }

        override fun <T> serialize(strategy: SerializationStrategy<T>, value: T): SerializedPayload {
            return create(supportedSerializationFormats.first()).serialize(strategy, value)
        }
    }

    interface CombinedSerializer {
        val transportFrameSerializer: TransportFrameSerializer

        val payloadSerializer: PayloadSerializer

        interface Factory {
            val format: SerializationFormat

            fun create(): CombinedSerializer
        }
    }
}

class JsonCombinedSerializer(builder: JsonBuilder.() -> Unit): SerializerRegistry.CombinedSerializer {
    override val transportFrameSerializer = JsonTransportFrameSerializer(builder)
    override val payloadSerializer = JsonPayloadSerializer(builder)

    class Factory(private val builder: JsonBuilder.() -> Unit = { }): SerializerRegistry.CombinedSerializer.Factory {
        override val format: SerializationFormat = SerializationFormat.Text.Json

        override fun create(): SerializerRegistry.CombinedSerializer {
            return JsonCombinedSerializer(builder)
        }
    }
}

class JsonTransportFrameSerializer(private val builder: JsonBuilder.() -> Unit = { }): TransportFrameSerializer {
    private val json = Json {
        encodeDefaults = false
        builder()
    }
    override val format = SerializationFormat.Text.Json

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

class JsonPayloadSerializer(private val builder: JsonBuilder.() -> Unit = { }): PayloadSerializer {
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