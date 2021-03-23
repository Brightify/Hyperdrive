package org.brightify.hyperdrive.krpc.description

import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer

interface CallDescription<PAYLOAD> {
    val identifier: ServiceCallIdentifier
    val payloadSerializer: KSerializer<PAYLOAD>
    val errorSerializer: RPCErrorSerializer
}