package org.brightify.hyperdrive.krpc.protocol

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer

public interface RPCProtocol {
    public val version: Version

    public suspend fun run()

    public fun singleCall(serviceCallIdentifier: ServiceCallIdentifier): RPC.SingleCall.Caller

    public fun upstream(serviceCallIdentifier: ServiceCallIdentifier): RPC.Upstream.Caller

    public fun downstream(serviceCallIdentifier: ServiceCallIdentifier): RPC.Downstream.Caller

    public fun bistream(serviceCallIdentifier: ServiceCallIdentifier): RPC.Bistream.Caller

    @Serializable
    public enum class Version(public val literal: Int) {
        Ascension(1),
    }

    public interface Factory {
        public val version: Version

        public fun create(
            connection: RPCConnection,
            frameSerializer: TransportFrameSerializer,
            implementationRegistry: RPCImplementationRegistry
        ): RPCProtocol
    }
}

