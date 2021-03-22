package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer

class RPCFrameSerializationStrategy<EVENT: RPCEvent>(
    private val headerSerializer: KSerializer<RPCFrame.Header<EVENT>>
): SerializationStrategy<OutgoingRPCFrame<EVENT>> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RPCFrame") {
        element("header", headerSerializer.descriptor)
        element("data", descriptor = buildSerialDescriptor("data", SerialKind.CONTEXTUAL) { })
    }

    override fun serialize(encoder: Encoder, value: OutgoingRPCFrame<EVENT>) {
        headerSerializer.serialize(encoder, value.header)
        value.serializationStrategy.serialize(encoder, value.data)
    }

    companion object {
        inline operator fun <reified EVENT: RPCEvent> invoke(): RPCFrameSerializationStrategy<EVENT> {
            return RPCFrameSerializationStrategy(RPCFrame.Header.serializer(PolymorphicSerializer(EVENT::class)))
        }
    }
}