package org.brightify.hyperdrive.krpc.api.error

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.api.UnexpectedRPCEventException

class RPCErrorSerializer(
    register: PolymorphicModuleBuilder<RPCError>.() -> Unit = { },
): KSerializer<Throwable> {

    private val module = SerializersModule {
        polymorphic(RPCError::class) {
            // TODO Register internal errors
            subclass(NonRPCErrorThrownError::class)
            subclass(UnknownRPCReferenceException::class)
            subclass(UnexpectedRPCEventException::class)
            subclass(RPCNotFoundError::class)
            register()
        }
    }

    private val statusCodeSerializer = RPCError.StatusCode.serializer()
    private val stringSerializer = String.serializer()

    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RPCError") {
        element("statusCode", statusCodeSerializer.descriptor)
        element("debugMessage", stringSerializer.descriptor)
        element("errorSerialName", stringSerializer.descriptor)
        element("error", descriptor = buildSerialDescriptor("error", SerialKind.CONTEXTUAL) { })
    }

    override fun deserialize(decoder: Decoder): RPCError {
        val statusCode = decoder.decodeSerializableValue(statusCodeSerializer)
        val debugMessage = decoder.decodeString()
        val errorSerialName = decoder.decodeString()

        val deserializationStrategy = module.getPolymorphic(RPCError::class, errorSerialName)
        return if (deserializationStrategy != null) {
            decoder.decodeSerializableValue(deserializationStrategy)
        } else {
            UnrecognizedRPCError(statusCode, debugMessage, errorSerialName)
        }
    }

    override fun serialize(encoder: Encoder, value: Throwable) {
        @Suppress("ThrowableNotThrown")
        val rpcError = value as? RPCError ?: NonRPCErrorThrownError(value)
        encoder.encodeSerializableValue(statusCodeSerializer, rpcError.statusCode)
        encoder.encodeString(rpcError.debugMessage)

        val serializationStrategy = module.getPolymorphic(RPCError::class, rpcError)
        if (serializationStrategy != null) {
            encoder.encodeString(serializationStrategy.descriptor.serialName)
            encoder.encodeSerializableValue(serializationStrategy, rpcError)
        } else {
            encoder.encodeString("# Unknown: ${value::class.simpleName ?: "N/A"}")
        }
    }
}