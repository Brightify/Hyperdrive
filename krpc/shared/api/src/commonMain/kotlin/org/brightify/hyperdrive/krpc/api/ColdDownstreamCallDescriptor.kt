package org.brightify.hyperdrive.krpc.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

data class ColdDownstreamCallDescriptor<REQUEST, SERVER_STREAM>(
    override val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>,
    val serverStreamSerializer: KSerializer<SERVER_STREAM>,
    override val errorSerializer: RPCErrorSerializer,
): LocalInStreamCallDescriptor<REQUEST> {

    override val payloadSerializer: KSerializer<REQUEST> = outgoingSerializer

    fun calling(method: suspend (REQUEST) -> Flow<SERVER_STREAM>): CallDescriptor<REQUEST> {
        return CallDescriptor.ColdDownstream(
            identifier,
            outgoingSerializer,
            serverStreamSerializer,
            errorSerializer,
            method,
        )
    }
}