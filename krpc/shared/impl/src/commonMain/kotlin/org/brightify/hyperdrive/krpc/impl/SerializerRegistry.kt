package org.brightify.hyperdrive.krpc.impl

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.application.PayloadSerializer
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer

public class SerializerRegistry(
    private val combinedSerializerFactories: List<CombinedSerializer.Factory>,
) {
    public constructor(vararg combinedSerializerFactories: CombinedSerializer.Factory): this(combinedSerializerFactories.toList())

    init {
        require(combinedSerializerFactories.toSet().count() == combinedSerializerFactories.count()) {
            "Only a single serializer for each format is supported!"
        }
    }

    private val supportedSerializationFormats = combinedSerializerFactories.map { it.format }
    private val combinedSerializers = mutableMapOf<SerializationFormat, CombinedSerializer>()

    public val transportFrameSerializerFactory: TransportFrameSerializer.Factory = object: TransportFrameSerializer.Factory {
        override val supportedSerializationFormats: List<SerializationFormat>
            get() = this@SerializerRegistry.supportedSerializationFormats

        override fun create(format: SerializationFormat): TransportFrameSerializer {
            require(supportedSerializationFormats.contains(format)) { "Format $format not supported." }

            return combinedSerializers.getOrPut(format) {
                combinedSerializerFactories.single { it.format == format }.create()
            }.transportFrameSerializer
        }
    }

    public val payloadSerializerFactory: PayloadSerializer.Factory = object: PayloadSerializer.Factory {
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

    public interface CombinedSerializer {
        public val transportFrameSerializer: TransportFrameSerializer

        public val payloadSerializer: PayloadSerializer

        public interface Factory {
            public val format: SerializationFormat

            public fun create(): CombinedSerializer
        }
    }
}