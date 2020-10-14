package org.brightify.hyperdrive.krpc.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

data class ColdUpstreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>(
    val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>,
    val clientStreamSerializer: KSerializer<CLIENT_STREAM>,
    val incomingSerializer: KSerializer<RESPONSE>,
    val errorSerializer: RPCErrorSerializer,
) {
    fun calling(method: suspend (REQUEST, Flow<CLIENT_STREAM>) -> RESPONSE): CallDescriptor {
        return CallDescriptor.ColdUpstream(
            identifier,
            outgoingSerializer,
            clientStreamSerializer,
            incomingSerializer,
            errorSerializer,
            method,
        )
    }
}