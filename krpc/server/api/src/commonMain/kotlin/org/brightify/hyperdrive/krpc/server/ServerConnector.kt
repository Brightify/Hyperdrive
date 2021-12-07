package org.brightify.hyperdrive.krpc.server

import org.brightify.hyperdrive.krpc.RPCConnection

public interface ServerConnector {
    public suspend fun nextConnection(): RPCConnection
}