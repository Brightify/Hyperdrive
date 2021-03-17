package org.brightify.hyperdrive.krpc.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

data class ColdUpstreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>(
    override val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>,
    override val clientStreamSerializer: KSerializer<CLIENT_STREAM>,
    val incomingSerializer: KSerializer<RESPONSE>,
    override val errorSerializer: RPCErrorSerializer,
): LocalOutStreamCallDescriptor<REQUEST, CLIENT_STREAM> {

    override val payloadSerializer: KSerializer<REQUEST> = outgoingSerializer

    fun calling(method: suspend (REQUEST, Flow<CLIENT_STREAM>) -> RESPONSE): CallDescriptor<REQUEST> {
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