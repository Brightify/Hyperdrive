package org.brightify.hyperdrive.krpc.protocol

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.RPCTransport

interface RPCProtocol: RPCTransport {
    val version: Version

    val isActive: Boolean

    suspend fun join()

    @Serializable
    enum class Version(val literal: Int) {
        Ascension(1),
    }

    interface Factory {
        val version: Version

        fun create(connection: RPCConnection): RPCProtocol
    }
}