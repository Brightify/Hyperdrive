package org.brightify.hyperdrive.krpc.description

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer

public data class ColdDownstreamCallDescription<REQUEST, SERVER_STREAM>(
    override val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>,
    val serverStreamSerializer: KSerializer<SERVER_STREAM>,
    override val errorSerializer: RPCErrorSerializer,
): DownstreamCallDescription<REQUEST> {

    override val payloadSerializer: KSerializer<REQUEST> = outgoingSerializer

    public fun calling(method: suspend (REQUEST) -> Flow<SERVER_STREAM>): RunnableCallDescription<REQUEST> {
        return RunnableCallDescription.ColdDownstream(
            identifier,
            outgoingSerializer,
            serverStreamSerializer,
            errorSerializer,
            method,
        )
    }
}