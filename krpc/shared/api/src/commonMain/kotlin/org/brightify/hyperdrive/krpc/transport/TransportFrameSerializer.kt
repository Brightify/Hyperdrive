package org.brightify.hyperdrive.krpc.transport

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.SerializedFrame

interface TransportFrameSerializer {
    val format: SerializationFormat

    fun <T> serialize(strategy: SerializationStrategy<T>, frame: T): SerializedFrame

    fun <T> deserialize(strategy: DeserializationStrategy<T>, frame: SerializedFrame): T

    interface Factory {
        val supportedSerializationFormats: List<SerializationFormat>

        fun create(format: SerializationFormat): TransportFrameSerializer
    }
}