package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.Serializable

interface RPCProtocol: RPCTransport {
    val version: Version

    val isActive: Boolean

    suspend fun join()

    suspend fun close()

    @Serializable
    enum class Version(val literal: Int) {
        Ascension(1),
    }

    interface Factory {
        val version: Version

        fun create(connection: RPCConnection): RPCProtocol
    }
}