package org.brightify.hyperdrive.krpc.description

import kotlinx.serialization.KSerializer

public interface UpstreamCallDescription<PAYLOAD, CLIENT_STREAM>: CallDescription<PAYLOAD> {
    public val clientStreamSerializer: KSerializer<CLIENT_STREAM>
}