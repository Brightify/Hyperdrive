package org.brightify.hyperdrive.krpc

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

@Serializable(with = SerializedPayload.Serializer::class)
public sealed class SerializedPayload {
    public abstract val format: SerializationFormat

    public class Binary(public val binary: ByteArray, override val format: SerializationFormat.Binary): SerializedPayload()
    public class Text(public val text: String, override val format: SerializationFormat.Text): SerializedPayload()

    public class Serializer: KSerializer<SerializedPayload> {
        private val binarySerializer = ByteArraySerializer()

        public override val descriptor: SerialDescriptor = buildClassSerialDescriptor("builtin:SerializablePayload") {
            element("format", String.serializer().descriptor)
            element("payload", ContextualSerializer(Any::class).descriptor)
        }

        public override fun serialize(encoder: Encoder, value: SerializedPayload) {
            if (encoder is JsonEncoder) {
                val payloadElement = when (value) {
                    is Binary -> encoder.json.encodeToJsonElement(binarySerializer, value.binary)
                    is Text -> if (value.format == SerializationFormat.Text.Json) {
                        Json.parseToJsonElement(value.text)
                    } else {
                        JsonPrimitive(value.text)
                    }
                }

                encoder.encodeJsonElement(
                    buildJsonObject {
                        put("format", value.format.readableIdentifier)
                        put("payload", payloadElement)
                    }
                )
            } else {
                encoder.encodeStructure(descriptor) {
                    encodeStringElement(descriptor, 0, value.format.readableIdentifier)
                    when (value) {
                        is Binary -> encodeSerializableElement(descriptor, 1, binarySerializer, value.binary)
                        is Text -> encodeStringElement(descriptor, 1, value.text)
                    }
                }
            }
        }

        public override fun deserialize(decoder: Decoder): SerializedPayload {
            return if (decoder is JsonDecoder) {
                val element = decoder.decodeJsonElement().jsonObject
                val formatIdentifier = element.getValue("format").jsonPrimitive.content
                val format = SerializationFormat(formatIdentifier)
                    ?: throw SerializationException("Unknown serialization format: $formatIdentifier")
                val payload = element.getValue("payload")
                when (format) {
                    is SerializationFormat.Binary -> Binary(decoder.json.decodeFromJsonElement(binarySerializer, payload),
                        format)
                    is SerializationFormat.Text -> Text(decoder.json.encodeToString(payload), format)
                }
            } else {
                decoder.decodeStructure(descriptor) {
                    var format: SerializationFormat? = null
                    var payload: SerializedPayload? = null

                    if (decodeSequentially()) {
                        val formatIdentifier = decodeStringElement(descriptor, 0)
                        format = SerializationFormat(formatIdentifier)
                            ?: throw SerializationException("Unknown serialization format: $formatIdentifier")
                        payload = when (format) {
                            is SerializationFormat.Binary -> Binary(decodeSerializableElement(descriptor,
                                1,
                                binarySerializer), format)
                            is SerializationFormat.Text -> Text(decodeStringElement(descriptor, 1), format)
                        }
                    } else {
                        while (true) {
                            when (val index = decodeElementIndex(descriptor)) {
                                0 -> {
                                    val formatIdentifier = decodeStringElement(descriptor, 0)
                                    format = SerializationFormat(formatIdentifier)
                                        ?: throw SerializationException("Unknown serialization format: $formatIdentifier")
                                }
                                1 -> payload = when(format) {
                                    is SerializationFormat.Binary -> Binary(decodeSerializableElement(descriptor,
                                        1,
                                        binarySerializer), format)
                                    is SerializationFormat.Text -> Text(decodeStringElement(descriptor, 1), format)
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
}