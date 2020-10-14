package org.brightify.hyperdrive.krpc.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

data class ColdDownstreamCallDescriptor<REQUEST, SERVER_STREAM>(
    val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>,
    val serverStreamSerializer: KSerializer<SERVER_STREAM>,
    val errorSerializer: RPCErrorSerializer,
) {
    fun calling(method: suspend (REQUEST) -> Flow<SERVER_STREAM>): CallDescriptor {
        return CallDescriptor.ColdDownstream(
            identifier,
            outgoingSerializer,
            serverStreamSerializer,
            errorSerializer,
            method,
        )
    }
}