package org.brightify.hyperdrive.krpc.description

import kotlinx.serialization.KSerializer

interface UpstreamCallDescription<PAYLOAD, CLIENT_STREAM>: CallDescription<PAYLOAD> {
    val clientStreamSerializer: KSerializer<CLIENT_STREAM>
}