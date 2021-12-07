package org.brightify.hyperdrive.krpc.transport

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.SerializedFrame

public interface TransportFrameSerializer {
    public val format: SerializationFormat

    public fun <T> serialize(strategy: SerializationStrategy<T>, frame: T): SerializedFrame

    public fun <T> deserialize(strategy: DeserializationStrategy<T>, frame: SerializedFrame): T

    public interface Factory {
        public val supportedSerializationFormats: List<SerializationFormat>

        public fun create(format: SerializationFormat): TransportFrameSerializer
    }
}