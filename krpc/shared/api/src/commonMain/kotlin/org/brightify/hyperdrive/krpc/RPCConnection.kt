package org.brightify.hyperdrive.krpc

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

interface RPCConnection: CoroutineScope {
    suspend fun close()

    suspend fun receive(): SerializedFrame

    suspend fun send(frame: SerializedFrame)
}

sealed class SerializedFrame {
    class Binary(val binary: ByteArray): SerializedFrame() {
        override fun toString(): String {
            return binary.asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }
        }
    }
    class Text(val text: String): SerializedFrame() {
        override fun toString(): String {
            return "SerializedFrame.Text($text)"
        }
    }
}

@Serializable(with = SerializablePayloadSerializer::class)
sealed class SerializedPayload {
    abstract val format: SerializationFormat

    class Binary(val binary: ByteArray, override val format: SerializationFormat.Binary): SerializedPayload()
    class Text(val text: String, override val format: SerializationFormat.Text): SerializedPayload()
}

class SerializablePayloadSerializer: KSerializer<SerializedPayload> {
    private val binarySerializer = ByteArraySerializer()

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("builtin:SerializablePayload") {
        element("format", String.serializer().descriptor)
        element("payload", ContextualSerializer(Any::class).descriptor)
    }

    override fun serialize(encoder: Encoder, value: SerializedPayload) {
        if (encoder is JsonEncoder) {
            val payloadElement = when (value) {
                is SerializedPayload.Binary -> encoder.json.encodeToJsonElement(binarySerializer, value.binary)
                is SerializedPayload.Text -> if (value.format == SerializationFormat.Text.Json) {
                    Json.parseToJsonElement(value.text)
                } else {
                    JsonPrimitive(value.text)
                }
            }

            encoder.encodeJsonElement(
                buildJsonObject {
                    put("format",  value.format.readableIdentifier)
                    put("payload", payloadElement)
                }
            )
        } else {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, value.format.readableIdentifier)
                when (value) {
                    is SerializedPayload.Binary -> encodeSerializableElement(descriptor, 1, binarySerializer, value.binary)
                    is SerializedPayload.Text -> encodeStringElement(descriptor, 1, value.text)
                }
            }
        }
    }

    override fun deserialize(decoder: Decoder): SerializedPayload {
        return if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement().jsonObject
            val formatIdentifier = element.getValue("format").jsonPrimitive.content
            val format = SerializationFormat(formatIdentifier) ?: throw SerializationException("Unknown serialization format: $formatIdentifier")
            val payload = element.getValue("payload")
            when (format) {
                is SerializationFormat.Binary -> SerializedPayload.Binary(decoder.json.decodeFromJsonElement(binarySerializer, payload), format)
                is SerializationFormat.Text -> SerializedPayload.Text(decoder.json.encodeToString(payload), format)
            }
        } else {
            decoder.decodeStructure(descriptor) {
                var format: SerializationFormat? = null
                var payload: SerializedPayload? = null

                if (decodeSequentially()) {
                    val formatIdentifier = decodeStringElement(descriptor, 0)
                    format = SerializationFormat(formatIdentifier) ?: throw SerializationException("Unknown serialization format: $formatIdentifier")
                    payload = when (format) {
                        is SerializationFormat.Binary -> SerializedPayload.Binary(decodeSerializableElement(descriptor, 1, binarySerializer), format)
                        is SerializationFormat.Text ->  SerializedPayload.Text(decodeStringElement(descriptor, 1), format)
                    }
                } else {
                    while (true) {
                        when (val index = decodeElementIndex(descriptor)) {
                            0 -> {
                                val formatIdentifier = decodeStringElement(descriptor, 0)
                                format = SerializationFormat(formatIdentifier) ?: throw SerializationException("Unknown serialization format: $formatIdentifier")
                            }
                            1 -> payload = when(format) {
                                is SerializationFormat.Binary -> SerializedPayload.Binary(decodeSerializableElement(descriptor, 1, binarySerializer), format)
                                is SerializationFormat.Text ->  SerializedPayload.Text(decodeStringElement(descriptor, 1), format)
                                null -> throw SerializationException("Payload cannot be deserialized before format.")
                            }
                            CompositeDecoder.DECODE_DONE -> break
                            else -> throw SerializationException("Unexpected index $index!")
                        }
                    }
                }

                requireNotNull(payload) { "Payload not deserialized!" }
            }
        }
    }
}

sealed class SerializationFormat {
    abstract val readableIdentifier: String
    abstract val identifier: Byte

    sealed class Binary(override val identifier: Byte, override val readableIdentifier: String): SerializationFormat() {
        object Protobuf: Binary(-1, "proto")
        object Cbor: Binary(-2, "cbor")
    }

    sealed class Text(override val identifier: Byte, override val readableIdentifier: String): SerializationFormat() {
        object Json: Text(1, "json")
        object Properties: Text(2, "properties")
        object Hocon: Text(3, "hocon")
    }

    companion object {
        private val allFormats by lazy {
            listOf(
                Binary.Protobuf,
                Binary.Cbor,
                Text.Json,
                Text.Properties,
                Text.Hocon,
            )
        }
        private val identifiedFormats: Map<Byte, SerializationFormat> by lazy { allFormats.associateBy { it.identifier } }
        private val readableIdentifiedFormats: Map<String, SerializationFormat> by lazy { allFormats.associateBy { it.readableIdentifier } }

        operator fun invoke(identifier: Byte): SerializationFormat? {
            return identifiedFormats[identifier]
        }

        operator fun invoke(readableIdentifier: String): SerializationFormat? {
            return readableIdentifiedFormats[readableIdentifier]
        }
    }
}
