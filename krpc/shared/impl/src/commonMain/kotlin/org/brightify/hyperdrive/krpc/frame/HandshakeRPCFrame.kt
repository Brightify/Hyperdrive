package org.brightify.hyperdrive.krpc.frame

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.protocol.RPCProtocol

public interface HandshakeRPCFrame: RPCFrame {

    public sealed class ProtocolSelection: HandshakeRPCFrame {

        @Serializable
        @SerialName("h:p:req")
        public class Request(public val supportedProtocolVersions: List<RPCProtocol.Version> = emptyList()): ProtocolSelection()

        @Serializable
        @SerialName("h:p:res")
        public sealed class Response: ProtocolSelection() {

            @Serializable
            @SerialName("h:p:res:ok")
            public class Success(public val selectedProtocolVersion: RPCProtocol.Version): Response()
            @Serializable
            @SerialName("h:p:res:err")
            public class Error(public val message: String): Response()
        }
    }

    @Serializable
    @SerialName("h:done")
    public sealed class Complete: HandshakeRPCFrame {
        @Serializable
        @SerialName("h:done:ok")
        public object Success: Complete()
        @Serializable
        @SerialName("h:done:err")
        public class Error(public val message: String): Complete()
    }
}