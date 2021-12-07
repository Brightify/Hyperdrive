package org.brightify.hyperdrive.krpc.application.runner

import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
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

public sealed class Response<SUCCESS> {
    public class Success<SUCCESS>(public val response: SUCCESS): Response<SUCCESS>()
    public class Error<SUCCESS>(public val error: RPCError): Response<SUCCESS>() {
        public constructor(throwable: Throwable): this(throwable.asRPCError())
    }

    public class Serializer<SUCCESS>(
        private val successSerializer: KSerializer<SUCCESS>,
        private val errorSerializer: RPCErrorSerializer,
    ): KSerializer<Response<SUCCESS>> {
        private companion object {
            const val element: Byte = 0
            const val complete: Byte = 1
            const val error: Byte = -1
        }

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("builtin:StreamEventSerializer") {
            element("successful", Boolean.serializer().descriptor)
            element("value", ContextualSerializer(Any::class).descriptor, isOptional = true)
        }

        override fun serialize(encoder: Encoder, value: Response<SUCCESS>) {
            encoder.encodeStructure(descriptor) {
                encodeBooleanElement(descriptor, 0, when (value) {
                    is Success -> true
                    is Error -> false
                })

                Do exhaustive when (value) {
                    is Success -> encodeSerializableElement(descriptor, 1, successSerializer, value.response)
                    is Error -> encodeSerializableElement(descriptor, 1, errorSerializer, value.error)
                }
            }
        }

        override fun deserialize(decoder: Decoder): Response<SUCCESS> {
            return decoder.decodeStructure(descriptor) {
                var isSuccess: Boolean? = null
                var response: Response<SUCCESS>? = null
                if (decodeSequentially()) {
                    isSuccess = decodeBooleanElement(descriptor, 0)
                    response = if (isSuccess) {
                        Success(
                            decodeSerializableElement(descriptor, 1, successSerializer)
                        )
                    } else {
                        Error(
                            decodeSerializableElement(descriptor, 1, errorSerializer)
                        )
                    }
                } else {
                    while (true) {
                        Do exhaustive when (val index = decodeElementIndex(descriptor)) {
                            0 -> isSuccess = decodeBooleanElement(descriptor, 0)
                            1 -> {
                                requireNotNull(isSuccess) { "Response value cannot be deserialized before its type." }
                                response = if (isSuccess) {
                                    Success(
                                        decodeSerializableElement(descriptor, 1, successSerializer)
                                    )
                                } else {
                                    Error(
                                        decodeSerializableElement(descriptor, 1, errorSerializer)
                                    )
                                }
                            }
                            CompositeDecoder.DECODE_DONE -> break
                            else -> error("Unknown index $index")
                        }
                    }
                }

                requireNotNull(response) { "Response not deserialized." }
            }
        }
    }

}
