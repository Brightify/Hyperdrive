package org.brightify.hyperdrive.krpc

public sealed class SerializedFrame {
    public class Binary(public val binary: ByteArray): SerializedFrame() {
        @OptIn(ExperimentalUnsignedTypes::class)
        public override fun toString(): String {
            return binary.asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }
        }
    }
    public class Text(public val text: String): SerializedFrame() {
        public override fun toString(): String {
            return "SerializedFrame.Text($text)"
        }
    }
}