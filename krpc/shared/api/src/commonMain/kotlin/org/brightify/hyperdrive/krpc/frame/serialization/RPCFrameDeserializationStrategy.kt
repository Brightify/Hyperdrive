package org.brightify.hyperdrive.krpc.frame.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import org.brightify.hyperdrive.krpc.frame.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.frame.RPCEvent
import org.brightify.hyperdrive.krpc.frame.RPCFrame

class RPCFrameDeserializationStrategy<EVENT: RPCEvent>(
    private val headerSerializer: KSerializer<RPCFrame.Header<EVENT>>
): DeserializationStrategy<IncomingRPCFrame<EVENT>> {

    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RPCFrame") {
        element("header", headerSerializer.descriptor)
        element("data", descriptor = buildSerialDescriptor("data", SerialKind.CONTEXTUAL) { })
    }

    override fun deserialize(decoder: Decoder): IncomingRPCFrame<EVENT> {
        val header = headerSerializer.deserialize(decoder)

        return IncomingRPCFrame(
            header,
            decoder
        )
    }

    companion object {
        inline operator fun <reified EVENT: RPCEvent> invoke(): RPCFrameDeserializationStrategy<EVENT> {
            return RPCFrameDeserializationStrategy(RPCFrame.Header.serializer(PolymorphicSerializer(EVENT::class)))
        }
    }
}