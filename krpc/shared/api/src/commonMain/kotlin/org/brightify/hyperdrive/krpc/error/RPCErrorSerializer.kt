package org.brightify.hyperdrive.krpc.error

import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
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
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.brightify.hyperdrive.krpc.api.RPCError

class RPCErrorSerializer(
    register: PolymorphicModuleBuilder<RPCError>.() -> Unit = { },
): KSerializer<RPCError> {

    private val module = SerializersModule {
        polymorphic(RPCError::class) {
            subclass(InternalServerError::class, InternalServerError.serializer())
            subclass(RPCNotFoundError::class, RPCNotFoundError.serializer())
            subclass(RPCProtocolViolationError::class, RPCProtocolViolationError.serializer())
            subclass(RPCStreamTimeoutError::class, RPCStreamTimeoutError.serializer())
            subclass(UnknownRPCReferenceException::class, UnknownRPCReferenceException.serializer())

            register()
        }
    }

    private val statusCodeSerializer = RPCError.StatusCode.serializer()
    private val stringSerializer = String.serializer()

    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("builtin:RPCError") {
        element("statusCode", statusCodeSerializer.descriptor)
        element("debugMessage", stringSerializer.descriptor)
        element("errorSerialName", stringSerializer.descriptor)
        element("error", ContextualSerializer(Any::class).descriptor, isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: RPCError) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, statusCodeSerializer, value.statusCode)
            encodeStringElement(descriptor, 1, value.debugMessage)

            val serializationStrategy = module.getPolymorphic(RPCError::class, value)
            if (serializationStrategy != null) {
                encodeStringElement(descriptor, 2, serializationStrategy.descriptor.serialName)
                encodeSerializableElement(descriptor, 3, serializationStrategy, value)
            } else {
                val serialName = "unknown:${value::class.simpleName ?: "n/a"}"
                encodeStringElement(descriptor, 2, serialName)
            }
        }
    }

    override fun deserialize(decoder: Decoder): RPCError {
        return decoder.decodeStructure(descriptor) {
            var statusCode: RPCError.StatusCode? = null
            var debugMessage: String? = null
            var errorSerialName: String? = null
            var error: RPCError? = null

            if (decodeSequentially()) {
                statusCode = decodeSerializableElement(descriptor, 0, statusCodeSerializer)
                debugMessage = decodeStringElement(descriptor, 1)
                errorSerialName = decodeStringElement(descriptor, 2)
                val deserializationStrategy = module.getPolymorphic(RPCError::class, serializedClassName = errorSerialName)
                if (deserializationStrategy != null) {
                    error = decodeSerializableElement(descriptor, 3, deserializationStrategy)
                }
            } else {
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> statusCode = decodeSerializableElement(descriptor, 0, statusCodeSerializer)
                        1 -> debugMessage = decodeStringElement(descriptor, 1)
                        2 -> errorSerialName = decodeStringElement(descriptor, 2)
                        3 -> {
                            requireNotNull(errorSerialName) { "Cannot decode error before decoding its serial name." }
                            val deserializationStrategy = module.getPolymorphic(RPCError::class, serializedClassName = errorSerialName)
                            if (deserializationStrategy != null) {
                                error = decodeSerializableElement(descriptor, 3, deserializationStrategy)
                            }
                        }
                        CompositeDecoder.DECODE_DONE -> break
                        else -> error("Unknown index $index")
                    }
                }
            }

            if (error != null) {
                error
            } else {
                requireNotNull(statusCode) { "StatusCode is required to decode an unrecognized rpc error." }
                requireNotNull(debugMessage) { "DebugMessage is required to decode an unrecognized rpc error." }
                requireNotNull(errorSerialName) { "SerialName is required to decode an unrecognized rpc error." }

                UnrecognizedRPCError(statusCode, debugMessage, errorSerialName)
            }
        }
    }
}