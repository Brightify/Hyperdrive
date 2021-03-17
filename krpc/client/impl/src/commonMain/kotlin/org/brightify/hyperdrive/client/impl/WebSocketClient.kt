package org.brightify.hyperdrive.client.impl

import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.brightify.hyperdrive.krpc.api.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.api.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.api.RPCEvent
import org.brightify.hyperdrive.krpc.api.RPCClientConnector
import org.brightify.hyperdrive.krpc.api.RPCConnection
import org.brightify.hyperdrive.krpc.api.WebSocketFrameConverter
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class WebSocketClient(
    private val connectionScope: CoroutineScope,
    private val host: String = "localhost",
    private val port: Int = 8000,
    private val path: String = "/",
    private val frameConverter: WebSocketFrameConverter<Frame, RPCEvent, RPCEvent>
): RPCClientConnector {
    private val httpClient = HttpClient {
        install(WebSockets)
    }

    private var activeConnection: Connection? = null
    private var connectingMutex = Mutex()

    override suspend fun withConnection(block: suspend RPCConnection.() -> Unit) {
        val connection = connect()
        connection.block()
        connection.close()
        activeConnection = null
    }

    private suspend fun connect(): Connection {
        return connectingMutex.withLock {
            val oldConnection = activeConnection
            if (oldConnection != null && oldConnection.isActive) {
                oldConnection.close()
            }
            val newConnection = Connection(httpClient.webSocketSession(host = host, port = port, path = path))
            activeConnection = newConnection
            return@withLock newConnection
        }
    }

    inner class Connection(
        private val session: ClientWebSocketSession
    ): RPCConnection, CoroutineScope by session {
        override suspend fun close() {
            session.close()
        }

        override suspend fun send(frame: OutgoingRPCFrame<RPCEvent>) {
            session.send(frameConverter.rpcFrameToWebSocketFrame(frame))
        }

        override suspend fun receive(): IncomingRPCFrame<RPCEvent> {
            val frame = session.incoming.receive()
            return frameConverter.rpcFrameFromWebSocketFrame(frame)
        }
    }
}