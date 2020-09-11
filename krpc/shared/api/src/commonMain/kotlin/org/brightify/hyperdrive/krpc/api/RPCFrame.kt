package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

interface IncomingUpstreamRequest {
    suspend fun accept(): RPCFrame<RPCEvent.Downstream>
}

interface IncomingDownstreamResponse {
    fun accept()
}

data class RPCFrame<out EVENT: RPCEvent>(
    val header: Header<EVENT>,
    val dataSerializer: KSerializer<out @Contextual Any?>?,
    val data: @Contextual Any?
) {
    @Serializable
    data class Header<out EVENT: RPCEvent>(
        val index: RPCFrameIndex,
        val event: EVENT
    )
}

interface RPCSerializerResolver<EVENT: RPCEvent> {
    fun serializerFor(header: RPCFrame.Header<EVENT>): KSerializer<Any?>?
}

class RPCFrameSerializer<EVENT: RPCEvent>(
    private val headerSerializer: KSerializer<RPCFrame.Header<EVENT>>,
    private val serializerResolver: RPCSerializerResolver<EVENT>
): KSerializer<RPCFrame<EVENT>> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RPCFrame") {
        element("header", headerSerializer.descriptor)
        element("data", descriptor = buildSerialDescriptor("data", SerialKind.CONTEXTUAL) { })
    }

    override fun deserialize(decoder: Decoder): RPCFrame<EVENT> {
        val header = headerSerializer.deserialize(decoder)
        val serializer = serializerResolver.serializerFor(header)

        return RPCFrame(
            header,
            serializer,
            serializer?.deserialize(decoder)
        )
    }

    override fun serialize(encoder: Encoder, value: RPCFrame<EVENT>) {
        headerSerializer.serialize(encoder, value.header)
        (value.dataSerializer as KSerializer<Any?>?)?.serialize(encoder, value.data)
    }

    companion object {
        inline operator fun <reified EVENT: RPCEvent> invoke(serializerResolver: RPCSerializerResolver<EVENT>): RPCFrameSerializer<EVENT> {
            return RPCFrameSerializer(serializer(), serializerResolver)
        }
    }
}