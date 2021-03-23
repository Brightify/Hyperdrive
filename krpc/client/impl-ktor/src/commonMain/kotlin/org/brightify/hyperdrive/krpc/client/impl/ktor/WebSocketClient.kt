package org.brightify.hyperdrive.krpc.client.impl.ktor

import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.http.cio.websocket.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.brightify.hyperdrive.krpc.frame.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.frame.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.frame.RPCEvent
import org.brightify.hyperdrive.krpc.client.RPCClientConnector
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class WebSocketClient(
    private val host: String = "localhost",
    private val port: Int = 8000,
    private val path: String = "/",
    private val frameConverter: org.brightify.hyperdrive.krpc.api.WebSocketFrameConverter<Frame>
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

    override fun isConnectionCloseException(throwable: Throwable): Boolean {
        return throwable is EOFException || throwable is io.ktor.utils.io.errors.EOFException
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