package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

data class ClientCallDescriptor<REQUEST, RESPONSE>(
    val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>,
    val incomingSerializer: KSerializer<RESPONSE>,
    val errorSerializer: RPCErrorSerializer,
) {
    fun calling(method: suspend (REQUEST) -> RESPONSE): CallDescriptor {
        return CallDescriptor.Single(
            identifier,
            outgoingSerializer,
            incomingSerializer,
            errorSerializer,
            method,
        )
    }
}