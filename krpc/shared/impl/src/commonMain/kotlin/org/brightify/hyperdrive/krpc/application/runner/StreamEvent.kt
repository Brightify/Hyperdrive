package org.brightify.hyperdrive.krpc.application.runner

import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.error.asRPCError
import org.brightify.hyperdrive.utils.Do

public sealed class StreamEvent<ELEMENT> {
    public class Element<ELEMENT>(public val element: ELEMENT): StreamEvent<ELEMENT>()
    public class Complete<ELEMENT>: StreamEvent<ELEMENT>()
    public class Error<ELEMENT>(public val error: RPCError): StreamEvent<ELEMENT>() {
        public constructor(throwable: Throwable): this(throwable.asRPCError())
    }

    public class Serializer<ELEMENT>(
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
                    is Element -> element
                    is Complete -> complete
                    is Error -> error
                })

                when (value) {
                    is Element -> encodeSerializableElement(descriptor, 1, elementSerializer, value.element)
                    is Complete -> { }
                    is Error -> encodeSerializableElement(descriptor, 1, errorSerializer, value.error)
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
                        element -> Element(
                            decodeSerializableElement(descriptor, 1, elementSerializer)
                        )
                        complete -> Complete()
                        error -> Error(
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
                                    element -> Element(
                                        decodeSerializableElement(descriptor, 1, elementSerializer)
                                    )
                                    complete -> Complete()
                                    error -> Error(
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
                    Complete()
                } else {
                    requireNotNull(event) { "StreamEvent not deserialized." }
                }
            }
        }
    }
}


