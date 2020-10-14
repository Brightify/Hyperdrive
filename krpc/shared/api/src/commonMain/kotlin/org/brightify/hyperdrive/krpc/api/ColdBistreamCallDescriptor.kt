package org.brightify.hyperdrive.krpc.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

data class ColdBistreamCallDescriptor<REQUEST, CLIENT_STREAM, SERVER_STREAM>(
    val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>,
    val clientStreamSerializer: KSerializer<CLIENT_STREAM>,
    val serverStreamSerializer: KSerializer<SERVER_STREAM>,
    val errorSerializer: RPCErrorSerializer,
) {
    fun calling(method: suspend (REQUEST, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>): CallDescriptor {
        return CallDescriptor.ColdBistream(
            identifier,
            outgoingSerializer,
            clientStreamSerializer,
            serverStreamSerializer,
            errorSerializer,
            method,
        )
    }
}