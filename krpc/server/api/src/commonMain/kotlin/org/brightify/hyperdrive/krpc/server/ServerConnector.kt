package org.brightify.hyperdrive.krpc.server

import org.brightify.hyperdrive.krpc.RPCConnection

interface ServerConnector {
    suspend fun nextConnection(): RPCConnection
}