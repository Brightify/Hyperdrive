package org.brightify.hyperdrive.krpc

public sealed class SerializationFormat {
    public abstract val readableIdentifier: String
    public abstract val identifier: Byte

    public sealed class Binary(override val identifier: Byte, override val readableIdentifier: String): SerializationFormat() {
        public object Protobuf: Binary(-1, "proto")
        public object Cbor: Binary(-2, "cbor")
    }

    public sealed class Text(override val identifier: Byte, override val readableIdentifier: String): SerializationFormat() {
        public object Json: Text(1, "json")
        public object Properties: Text(2, "properties")
        public object Hocon: Text(3, "hocon")
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