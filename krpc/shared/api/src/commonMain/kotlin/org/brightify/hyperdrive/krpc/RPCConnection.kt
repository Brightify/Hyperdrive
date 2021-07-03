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

public interface RPCConnection: CoroutineScope {
    public suspend fun close()

    public suspend fun receive(): SerializedFrame

    public suspend fun send(frame: SerializedFrame)
}

public sealed class SerializedFrame {
    public class Binary(val binary: ByteArray): SerializedFrame() {
        @OptIn(ExperimentalUnsignedTypes::class)
        public override fun toString(): String {
            return binary.asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }
        }
    }
    public class Text(val text: String): SerializedFrame() {
        public override fun toString(): String {
            return "SerializedFrame.Text($text)"
        }
    }
}

@Serializable(with = SerializablePayloadSerializer::class)
public sealed class SerializedPayload {
    public abstract val format: SerializationFormat

    public class Binary(val binary: ByteArray, override val format: SerializationFormat.Binary): SerializedPayload()
    public class Text(val text: String, override val format: SerializationFormat.Text): SerializedPayload()
}

public class SerializablePayloadSerializer: KSerializer<SerializedPayload> {
    private val binarySerializer = ByteArraySerializer()

    public override val descriptor: SerialDescriptor = buildClassSerialDescriptor("builtin:SerializablePayload") {
        element("format", String.serializer().descriptor)
        element("payload", ContextualSerializer(Any::class).descriptor)
    }

    public override fun serialize(encoder: Encoder, value: SerializedPayload) {
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

    public override fun deserialize(decoder: Decoder): SerializedPayload {
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

public sealed class SerializationFormat {
    public abstract val readableIdentifier: String
    public abstract val identifier: Byte

    public sealed class Binary(override val identifier: Byte, override val readableIdentifier: String): SerializationFormat() {
        object Protobuf: Binary(-1, "proto")
        object Cbor: Binary(-2, "cbor")
    }

    public sealed class Text(override val identifier: Byte, override val readableIdentifier: String): SerializationFormat() {
        object Json: Text(1, "json")
        object Properties: Text(2, "properties")
        object Hocon: Text(3, "hocon")
    }

    public companion object {
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

        public operator fun invoke(identifier: Byte): SerializationFormat? {
            return identifiedFormats[identifier]
        }

        public operator fun invoke(readableIdentifier: String): SerializationFormat? {
            return readableIdentifiedFormats[readableIdentifier]
        }
    }
}
