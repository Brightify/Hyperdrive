package org.brightify.hyperdrive.krpc.description

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer

public data class ColdBistreamCallDescription<REQUEST, CLIENT_STREAM, SERVER_STREAM>(
    override val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>,
    override val clientStreamSerializer: KSerializer<CLIENT_STREAM>,
    val serverStreamSerializer: KSerializer<SERVER_STREAM>,
    override val errorSerializer: RPCErrorSerializer,
): DownstreamCallDescription<REQUEST>, UpstreamCallDescription<REQUEST, CLIENT_STREAM> {

    override val payloadSerializer: KSerializer<REQUEST> = outgoingSerializer

    public fun calling(method: suspend (REQUEST, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>): RunnableCallDescription<REQUEST> {
        return RunnableCallDescription.ColdBistream(
            identifier,
            outgoingSerializer,
            clientStreamSerializer,
            serverStreamSerializer,
            errorSerializer,
            method,
        )
    }
}