package org.brightify.hyperdrive.krpc.application

import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.protocol.RPCProtocol
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer

public interface RPCHandshakePerformer {
    public sealed class HandshakeResult {
        public class Success(
            public val selectedFrameSerializer: TransportFrameSerializer,
            public val selectedProtocolFactory: RPCProtocol.Factory,
        ): HandshakeResult()
        public class Failed(public val message: String): HandshakeResult()
    }

    public suspend fun performHandshake(connection: RPCConnection): HandshakeResult

    public class NoHandshake(
        public val selectedFrameSerializer: TransportFrameSerializer,
        public val selectedProtocolFactory: RPCProtocol.Factory,
    ): RPCHandshakePerformer {
        override suspend fun performHandshake(connection: RPCConnection): HandshakeResult {
            return HandshakeResult.Success(selectedFrameSerializer, selectedProtocolFactory)
        }
    }
}