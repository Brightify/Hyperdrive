package org.brightify.hyperdrive.krpc.frame

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.protocol.RPCProtocol

public interface HandshakeRPCFrame: RPCFrame {

    public sealed class ProtocolSelection: HandshakeRPCFrame {

        @Serializable
        public class Request(public val supportedProtocolVersions: List<RPCProtocol.Version> = emptyList()): ProtocolSelection()

        @Serializable
        public sealed class Response: ProtocolSelection() {

            @Serializable
            public class Success(public val selectedProtocolVersion: RPCProtocol.Version): Response()
            @Serializable
            public class Error(public val message: String): Response()
        }
    }

    @Serializable
    public sealed class Complete: HandshakeRPCFrame {
        @Serializable
        public object Success: Complete()
        @Serializable
        public class Error(public val message: String): Complete()
    }
}