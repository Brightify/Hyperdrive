package org.brightify.hyperdrive.krpc.session

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@OptIn(ExperimentalSerializationApi::class)
private object ContextUpdateSerializerDescriptor {
    val keySerializer = String.serializer()
    val modificationDescriptor = buildClassSerialDescriptor("org.brightify.hyperdrive.krpc.api.ContextUpdate.Modification") {
        element<ModificationType>("type")
        element<Int>("oldRevision", isOptional = true)
        element<Int>("newRevision", isOptional = true)
        element<Int>("newValue", isOptional = true)
    }
    val mapDescriptor = mapSerialDescriptor(
        keyDescriptor = keySerializer.descriptor,
        valueDescriptor = modificationDescriptor,
    )

    val descriptor: SerialDescriptor = buildClassSerialDescriptor("org.brightify.hyperdrive.krpc.api.ContextUpdate") {
        element(
            "updates",
            mapDescriptor,
        )
    }

    @Serializable
    enum class ModificationType {
        Set, Remove
    }
}

interface ContextKeyRegistry {
    fun getKeyByQualifiedName(keyQualifiedName: String): Session.Context.Key<*>
}

@OptIn(ExperimentalSerializationApi::class)
class IncomingContextUpdateSerializer(
    private val contextKeyRegistry: ContextKeyRegistry,
): DeserializationStrategy<IncomingContextUpdate> {
    override val descriptor: SerialDescriptor = ContextUpdateSerializerDescriptor.descriptor

    override fun deserialize(decoder: Decoder): IncomingContextUpdate {
        return IncomingContextUpdate(
            updates = merge(decoder, null)
        )
    }

    private fun merge(decoder: Decoder, previous: MutableMap<Session.Context.Key<*>, IncomingContextUpdate.Modification>?): MutableMap<Session.Context.Key<*>, IncomingContextUpdate.Modification> {
        val builder = previous ?: hashMapOf()
        val startIndex = builder.size
        val compositeDecoder = decoder.beginStructure(ContextUpdateSerializerDescriptor.mapDescriptor)
        if (compositeDecoder.decodeSequentially()) {
            readAll(compositeDecoder, builder, startIndex, readSize(compositeDecoder))
        } else {
            while (true) {
                val index = compositeDecoder.decodeElementIndex(ContextUpdateSerializerDescriptor.mapDescriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                readElement(compositeDecoder, startIndex + index, builder)
            }
        }
        compositeDecoder.endStructure(ContextUpdateSerializerDescriptor.mapDescriptor)
        return builder
    }

    private fun readSize(decoder: CompositeDecoder): Int {
        return decoder.decodeCollectionSize(ContextUpdateSerializerDescriptor.mapDescriptor)
    }

    private fun readAll(decoder: CompositeDecoder, builder: MutableMap<Session.Context.Key<*>, IncomingContextUpdate.Modification>, startIndex: Int, size: Int) {
        require(size >= 0) { "Size must be known in advance when using READ_ALL" }
        for (index in 0 until size * 2 step 2) {
            readElement(decoder, startIndex + index, builder, checkIndex = false)
        }
    }

    private fun readElement(decoder: CompositeDecoder, index: Int, builder: MutableMap<Session.Context.Key<*>, IncomingContextUpdate.Modification>, checkIndex: Boolean = true) {
        val keyQualifiedName: String = decoder.decodeSerializableElement(ContextUpdateSerializerDescriptor.mapDescriptor, index,
            ContextUpdateSerializerDescriptor.keySerializer)
        val key = contextKeyRegistry.getKeyByQualifiedName(keyQualifiedName)
        val vIndex = if (checkIndex) {
            decoder.decodeElementIndex(ContextUpdateSerializerDescriptor.mapDescriptor).also {
                require(it == index + 1) { "Value must follow key in a map, index for key: $index, returned index for value: $it" }
            }
        } else {
            index + 1
        }
        val modificationDeserializer = ModificationDeserializer(key.serializer)
        val value: IncomingContextUpdate.Modification = if (builder.containsKey(key) && modificationDeserializer.descriptor.kind !is PrimitiveKind) {
            decoder.decodeSerializableElement(ContextUpdateSerializerDescriptor.mapDescriptor, vIndex, modificationDeserializer, builder.getValue(key))
        } else {
            decoder.decodeSerializableElement(ContextUpdateSerializerDescriptor.mapDescriptor, vIndex, modificationDeserializer)
        }
        builder[key] = value
    }

    inner class ModificationDeserializer<VALUE: Any>(
        private val valueDeserializer: DeserializationStrategy<VALUE>,
    ): DeserializationStrategy<IncomingContextUpdate.Modification> {
        override val descriptor: SerialDescriptor = ContextUpdateSerializerDescriptor.modificationDescriptor

        override fun deserialize(decoder: Decoder): IncomingContextUpdate.Modification {
            val type = decoder.decodeSerializableValue(ContextUpdateSerializerDescriptor.ModificationType.serializer())
            return when (type) {
                ContextUpdateSerializerDescriptor.ModificationType.Set -> {
                    IncomingContextUpdate.Modification.Set(
                        if (decoder.decodeNotNullMark()) {
                            decoder.decodeInt()
                        } else {
                            decoder.decodeNull()
                        },
                        decoder.decodeInt(),
                        decoder.decodeSerializableValue(valueDeserializer)
                    )
                }
                ContextUpdateSerializerDescriptor.ModificationType.Remove -> {
                    IncomingContextUpdate.Modification.Remove(
                        decoder.decodeInt()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
class OutgoingContextUpdateSerializer: SerializationStrategy<OutgoingContextUpdate> {
    override val descriptor: SerialDescriptor = ContextUpdateSerializerDescriptor.descriptor

    override fun serialize(encoder: Encoder, value: OutgoingContextUpdate) {
        val size = value.updates.count()
        val composite = encoder.beginCollection(ContextUpdateSerializerDescriptor.mapDescriptor, size)
        var index = 0
        value.updates.forEach { (k, v) ->
            val modificationSerializer = ModificationSerializer(k.serializer)
            composite.encodeSerializableElement(ContextUpdateSerializerDescriptor.mapDescriptor, index++,
                ContextUpdateSerializerDescriptor.keySerializer, k.qualifiedName)
            composite.encodeSerializableElement(ContextUpdateSerializerDescriptor.mapDescriptor, index++, modificationSerializer, v)
        }
        composite.endStructure(descriptor)
    }

    class ModificationSerializer<VALUE: Any>(
        private val valueSerializer: SerializationStrategy<VALUE>,
    ): SerializationStrategy<OutgoingContextUpdate.Modification<VALUE>> {
        override val descriptor: SerialDescriptor = ContextUpdateSerializerDescriptor.modificationDescriptor

        override fun serialize(encoder: Encoder, value: OutgoingContextUpdate.Modification<VALUE>) {
            when (value) {
                is OutgoingContextUpdate.Modification.Set -> {
                    encoder.encodeSerializableValue(
                        ContextUpdateSerializerDescriptor.ModificationType.serializer(),
                        ContextUpdateSerializerDescriptor.ModificationType.Set,
                    )
                    if (value.oldRevision != null) {
                        encoder.encodeInt(value.oldRevision)
                    } else {
                        encoder.encodeNull()
                    }

                    encoder.encodeInt(value.newRevision)
                    encoder.encodeSerializableValue(valueSerializer, value.newValue)
                }
                is OutgoingContextUpdate.Modification.Remove -> {
                    encoder.encodeSerializableValue(
                        ContextUpdateSerializerDescriptor.ModificationType.serializer(),
                        ContextUpdateSerializerDescriptor.ModificationType.Remove,
                    )
                    encoder.encodeInt(value.oldRevision)
                }
            }
        }
    }
}
