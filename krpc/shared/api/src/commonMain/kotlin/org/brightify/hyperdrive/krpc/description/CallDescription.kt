package org.brightify.hyperdrive.krpc.description

import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer

public interface CallDescription<PAYLOAD> {
    public val identifier: ServiceCallIdentifier
    public val payloadSerializer: KSerializer<PAYLOAD>
    public val errorSerializer: RPCErrorSerializer
}