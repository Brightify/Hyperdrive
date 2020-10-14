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

interface RPCFrame<out EVENT: RPCEvent> {
    val header: Header<EVENT>

    @Serializable
    data class Header<out EVENT: RPCEvent>(
        val callReference: RPCReference,
        val event: EVENT
    )
}

data class OutgoingRPCFrame<out EVENT: RPCEvent>(
    override val header: RPCFrame.Header<EVENT>,
    val serializationStrategy: SerializationStrategy<Any?>,
    val data: @Contextual Any?
): RPCFrame<EVENT>

data class IncomingRPCFrame<out EVENT: RPCEvent>(
    override val header: RPCFrame.Header<EVENT>,
    val decoder: Decoder
): RPCFrame<EVENT>

interface RPCSerializerResolver<EVENT: RPCEvent> {
    fun serializerFor(header: RPCFrame.Header<EVENT>): KSerializer<Any?>?
}

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
            return RPCFrameDeserializationStrategy(serializer())
        }
    }
}

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
            return RPCFrameSerializationStrategy(serializer())
        }
    }
}

//class RPCFrameSerializer<EVENT: RPCEvent>(
//    private val headerSerializer: KSerializer<RPCFrame.Header<EVENT>>,
//    private val serializationStrategy: RPCFrameSerializationStrategy<EVENT>,
//    private val deserializationStrategy: RPCFrameDeserializationStrategy<EVENT>
//): KSerializer<RPCFrame<EVENT>> {
//    @OptIn(InternalSerializationApi::class)
//    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RPCFrame") {
//        element("header", headerSerializer.descriptor)
//        element("data", descriptor = buildSerialDescriptor("data", SerialKind.CONTEXTUAL) { })
//    }
//
//    override fun deserialize(decoder: Decoder): IncomingRPCFrame<EVENT> = deserializationStrategy.deserialize(decoder)
//
//    override fun serialize(encoder: Encoder, value: RPCFrame<EVENT>) = serializationStrategy.serialize(encoder, value as OutgoingRPCFrame<EVENT>)
//
//    companion object {
//        inline operator fun <reified EVENT: RPCEvent> invoke(serializerResolver: RPCSerializerResolver<EVENT>): RPCFrameSerializer<EVENT> {
//            return RPCFrameSerializer(serializer(), RPCFrameSerializationStrategy(), RPCFrameDeserializationStrategy())
//        }
//    }
//}

//
//
//class RPCFrameSerializer<EVENT: RPCEvent>(
//    private val headerSerializer: KSerializer<RPCFrame.Header<EVENT>>,
//    private val serializerResolver: RPCSerializerResolver<EVENT>
//): KSerializer<RPCFrame<EVENT>> {
//    @OptIn(InternalSerializationApi::class)
//    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RPCFrame") {
//        element("header", headerSerializer.descriptor)
//        element("data", descriptor = buildSerialDescriptor("data", SerialKind.CONTEXTUAL) { })
//    }
//
//    override fun deserialize(decoder: Decoder): RPCFrame<EVENT> {
//        val header = headerSerializer.deserialize(decoder)
//        val serializer = serializerResolver.serializerFor(header)
//
//        return RPCFrame(
//            header,
//            serializer,
//            serializer?.deserialize(decoder)
//        )
//    }
//
//    override fun serialize(encoder: Encoder, value: RPCFrame<EVENT>) {
//        headerSerializer.serialize(encoder, value.header)
//        (value.dataSerializer as KSerializer<Any?>?)?.serialize(encoder, value.data)
//    }
//
//    companion object {
//        inline operator fun <reified EVENT: RPCEvent> invoke(serializerResolver: RPCSerializerResolver<EVENT>): RPCFrameSerializer<EVENT> {
//            return RPCFrameSerializer(serializer(), serializerResolver)
//        }
//    }
//}