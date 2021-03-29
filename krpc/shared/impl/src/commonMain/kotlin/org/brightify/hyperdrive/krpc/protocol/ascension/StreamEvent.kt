package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.error.InternalServerError
import org.brightify.hyperdrive.krpc.error.RPCError
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer
import org.brightify.hyperdrive.utils.Do

sealed class StreamEvent<ELEMENT> {
    class Element<ELEMENT>(val element: ELEMENT): StreamEvent<ELEMENT>()
    class Complete<ELEMENT>: StreamEvent<ELEMENT>()
    class Error<ELEMENT>(val error: RPCError): StreamEvent<ELEMENT>() {
        constructor(throwable: Throwable): this(throwable.RPCError())
    }
}

class StreamEventSerializer<ELEMENT>(
    private val elementSerializer: KSerializer<ELEMENT>,
    private val errorSerializer: RPCErrorSerializer,
): KSerializer<StreamEvent<ELEMENT>> {
    private companion object {
        const val element: Byte = 0
        const val complete: Byte = 1
        const val error: Byte = -1
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("builtin:StreamEventSerializer") {
        element("type", Byte.serializer().descriptor)
        element("value", ContextualSerializer(Any::class).descriptor, isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: StreamEvent<ELEMENT>) {
        encoder.encodeStructure(descriptor) {
            encodeByteElement(descriptor, 0, when (value) {
                is StreamEvent.Element -> element
                is StreamEvent.Complete -> complete
                is StreamEvent.Error -> error
            })

            when (value) {
                is StreamEvent.Element -> encodeSerializableElement(descriptor, 1, elementSerializer, value.element)
                is StreamEvent.Complete -> { }
                is StreamEvent.Error -> encodeSerializableElement(descriptor, 1, errorSerializer, value.error)
            }
        }
    }

    override fun deserialize(decoder: Decoder): StreamEvent<ELEMENT> {
        return decoder.decodeStructure(descriptor) {
            var type: Byte? = null
            var event: StreamEvent<ELEMENT>? = null
            if (decodeSequentially()) {
                type = decodeByteElement(descriptor, 0)
                event = when (type) {
                    element -> StreamEvent.Element(
                        decodeSerializableElement(descriptor, 1, elementSerializer)
                    )
                    complete -> StreamEvent.Complete()
                    error -> StreamEvent.Error(
                        decodeSerializableElement(descriptor, 1, errorSerializer)
                    )
                    else -> error("Unknown type $type")
                }
            } else {
                while (true) {
                    Do exhaustive when (val index = decodeElementIndex(descriptor)) {
                        0 -> type = decodeByteElement(descriptor, 0)
                        1 -> {
                            requireNotNull(type) { "Event value cannot be deserialized before its type." }
                            event = when (type) {
                                element -> StreamEvent.Element(
                                    decodeSerializableElement(descriptor, 1, elementSerializer)
                                )
                                complete -> StreamEvent.Complete()
                                error -> StreamEvent.Error(
                                    decodeSerializableElement(descriptor, 1, errorSerializer)
                                )
                                else -> error("Unknown type $type")
                            }
                        }
                        CompositeDecoder.DECODE_DONE -> break
                        else -> error("Unknown index $index")
                    }
                }
            }

            if (type == complete) {
                StreamEvent.Complete()
            } else {
                requireNotNull(event) { "StreamEvent not deserialized." }
            }
        }
    }
}
