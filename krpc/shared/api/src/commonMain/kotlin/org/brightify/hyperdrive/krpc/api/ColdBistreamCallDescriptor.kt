package org.brightify.hyperdrive.krpc.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

interface LocalOutStreamCallDescriptor<PAYLOAD, CLIENT_STREAM>: LocalCallDescriptor<PAYLOAD> {
    val clientStreamSerializer: KSerializer<CLIENT_STREAM>
}

data class ColdBistreamCallDescriptor<REQUEST, CLIENT_STREAM, SERVER_STREAM>(
    override val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>,
    override val clientStreamSerializer: KSerializer<CLIENT_STREAM>,
    val serverStreamSerializer: KSerializer<SERVER_STREAM>,
    override val errorSerializer: RPCErrorSerializer,
): LocalInStreamCallDescriptor<REQUEST>, LocalOutStreamCallDescriptor<REQUEST, CLIENT_STREAM> {

    override val payloadSerializer: KSerializer<REQUEST> = outgoingSerializer

    fun calling(method: suspend (REQUEST, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>): CallDescriptor<REQUEST> {
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