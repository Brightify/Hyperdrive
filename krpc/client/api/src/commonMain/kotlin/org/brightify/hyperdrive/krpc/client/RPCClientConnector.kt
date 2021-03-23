package org.brightify.hyperdrive.krpc.client

import org.brightify.hyperdrive.krpc.api.RPCConnection

interface RPCClientConnector {
    suspend fun withConnection(block: suspend RPCConnection.() -> Unit)

    fun isConnectionCloseException(throwable: Throwable): Boolean
}