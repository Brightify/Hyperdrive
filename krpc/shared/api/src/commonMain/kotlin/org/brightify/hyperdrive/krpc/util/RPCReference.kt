package org.brightify.hyperdrive.krpc.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = RPCReference.Serializer::class)
public data class RPCReference(val reference: UInt) {
    public class Serializer: KSerializer<RPCReference> {
        override val descriptor: SerialDescriptor = Int.serializer().descriptor

        override fun deserialize(decoder: Decoder): RPCReference {
            return RPCReference(decoder.decodeInt().toUInt())
        }

        override fun serialize(encoder: Encoder, value: RPCReference) {
            encoder.encodeInt(value.reference.toInt())
        }
    }
}

