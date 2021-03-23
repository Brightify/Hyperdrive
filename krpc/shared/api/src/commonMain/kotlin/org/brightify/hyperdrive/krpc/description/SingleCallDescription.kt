package org.brightify.hyperdrive.krpc.description

import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer

data class SingleCallDescription<REQUEST, RESPONSE>(
    override val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>,
    val incomingSerializer: KSerializer<RESPONSE>,
    override val errorSerializer: RPCErrorSerializer,
): CallDescription<REQUEST> {

    override val payloadSerializer: KSerializer<REQUEST> = outgoingSerializer

    fun calling(method: suspend (REQUEST) -> RESPONSE): RunnableCallDescription<REQUEST> {
        return RunnableCallDescription.Single(
            identifier,
            outgoingSerializer,
            incomingSerializer,
            errorSerializer,
            method,
        )
    }
}