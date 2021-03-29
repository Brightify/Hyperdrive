package org.brightify.hyperdrive.krpc.server.impl

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.server.ServerConnector

abstract class ProvidingServerConnector: ServerConnector {

    private val connectionChannel = Channel<RPCConnection>()

    final override suspend fun nextConnection(): RPCConnection {
        return connectionChannel.receive()
    }

    protected suspend fun provide(connection: RPCConnection) {
        connectionChannel.send(connection)
        connection.coroutineContext.job.join()
    }

}