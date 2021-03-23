package org.brightify.hyperdrive.krpc.description

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer

data class ColdUpstreamCallDescription<REQUEST, CLIENT_STREAM, RESPONSE>(
    override val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>,
    override val clientStreamSerializer: KSerializer<CLIENT_STREAM>,
    val incomingSerializer: KSerializer<RESPONSE>,
    override val errorSerializer: RPCErrorSerializer,
): UpstreamCallDescription<REQUEST, CLIENT_STREAM> {

    override val payloadSerializer: KSerializer<REQUEST> = outgoingSerializer

    fun calling(method: suspend (REQUEST, Flow<CLIENT_STREAM>) -> RESPONSE): RunnableCallDescription<REQUEST> {
        return RunnableCallDescription.ColdUpstream(
            identifier,
            outgoingSerializer,
            clientStreamSerializer,
            incomingSerializer,
            errorSerializer,
            method,
        )
    }
}