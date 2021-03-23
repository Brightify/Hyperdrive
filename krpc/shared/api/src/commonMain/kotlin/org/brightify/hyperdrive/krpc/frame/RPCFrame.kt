package org.brightify.hyperdrive.krpc.frame

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.util.RPCReference

interface RPCFrame<out EVENT: RPCEvent> {
    val header: Header<EVENT>

    @Serializable
    data class Header<out EVENT: RPCEvent>(
        val callReference: RPCReference,
        val event: EVENT
    )
}
