package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.Serializable

interface RPCFrame<out EVENT: RPCEvent> {
    val header: Header<EVENT>

    @Serializable
    data class Header<out EVENT: RPCEvent>(
        val callReference: RPCReference,
        val event: EVENT
    )
}
