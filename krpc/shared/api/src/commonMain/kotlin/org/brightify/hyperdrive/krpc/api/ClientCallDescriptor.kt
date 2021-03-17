package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

interface LocalCallDescriptor<PAYLOAD> {
    val identifier: ServiceCallIdentifier
    val payloadSerializer: KSerializer<PAYLOAD>
    val errorSerializer: RPCErrorSerializer
}

interface LocalInStreamCallDescriptor<PAYLOAD>: LocalCallDescriptor<PAYLOAD> {

}

data class ClientCallDescriptor<REQUEST, RESPONSE>(
    override val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>,
    val incomingSerializer: KSerializer<RESPONSE>,
    override val errorSerializer: RPCErrorSerializer,
): LocalCallDescriptor<REQUEST> {

    override val payloadSerializer: KSerializer<REQUEST> = outgoingSerializer

    fun calling(method: suspend (REQUEST) -> RESPONSE): CallDescriptor<REQUEST> {
        return CallDescriptor.Single(
            identifier,
            outgoingSerializer,
            incomingSerializer,
            errorSerializer,
            method,
        )
    }
}