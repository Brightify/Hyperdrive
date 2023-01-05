package org.brightify.hyperdrive.krpc.application

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.SerializedPayload

public interface PayloadSerializer {
    public val format: SerializationFormat

    public fun <T> serialize(strategy: SerializationStrategy<T>, payload: T): SerializedPayload

    public fun <T> deserialize(strategy: DeserializationStrategy<T>, payload: SerializedPayload): T

    public interface Factory {
        public val supportedSerializationFormats: List<SerializationFormat>

        public fun create(format: SerializationFormat): PayloadSerializer

        public fun <T> deserialize(strategy: DeserializationStrategy<T>, payload: SerializedPayload): T

        public fun <T> serialize(strategy: SerializationStrategy<T>, value: T): SerializedPayload
    }
}