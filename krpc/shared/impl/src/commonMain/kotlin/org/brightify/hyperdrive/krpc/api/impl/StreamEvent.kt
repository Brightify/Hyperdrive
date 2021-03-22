package org.brightify.hyperdrive.krpc.api.impl

import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.api.error.InternalServerError

sealed class StreamEvent<DATA> {
    data class Next<DATA>(
        val data: DATA
    ): StreamEvent<DATA>()
    class Complete<DATA>: StreamEvent<DATA>()
    class Error<DATA>(
        val error: RPCError
    ): StreamEvent<DATA>() {
        companion object {
            operator fun <DATA> invoke(throwable: Throwable): Error<DATA> {
                return Error(
                    throwable as? RPCError ?: InternalServerError(throwable)
                )
            }
        }
    }
}

class StreamEventSerializer<DATA>(
    private val dataSerializer: KSerializer<DATA>,
    private val errorSerializer: KSerializer<RPCError>,
): KSerializer<StreamEvent<DATA>> {
    private val eventTypeSerialier = EventType.serializer()

    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("org.brightify.hyperdrive.krpc.api.impl.StreamEvent") {
        element("type", eventTypeSerialier.descriptor)
        element("content", isOptional = true, descriptor = buildSerialDescriptor("org.brightify.hyperdrive.krpc.api.impl.StreamEvent.content", kind = SerialKind.CONTEXTUAL) { })
    }

    @Serializable
    private enum class EventType {
        Next, Complete, Error
    }

    override fun deserialize(decoder: Decoder): StreamEvent<DATA> {
        val eventType = decoder.decodeSerializableValue(eventTypeSerialier)

        return when (eventType) {
            EventType.Next -> StreamEvent.Next(decoder.decodeSerializableValue(dataSerializer))
            EventType.Complete -> StreamEvent.Complete()
            EventType.Error -> StreamEvent.Error(decoder.decodeSerializableValue(errorSerializer))
        }
    }

    override fun serialize(encoder: Encoder, value: StreamEvent<DATA>) {
        val eventType = when (value) {
            is StreamEvent.Next -> EventType.Next
            is StreamEvent.Complete -> EventType.Complete
            is StreamEvent.Error -> EventType.Error
        }

        encoder.encodeSerializableValue(eventTypeSerialier, eventType)

        Do exhaustive when (value) {
            is StreamEvent.Next -> encoder.encodeSerializableValue(dataSerializer, value.data)
            is StreamEvent.Complete -> { }
            is StreamEvent.Error -> encoder.encodeSerializableValue(errorSerializer, value.error)
        }
    }
}