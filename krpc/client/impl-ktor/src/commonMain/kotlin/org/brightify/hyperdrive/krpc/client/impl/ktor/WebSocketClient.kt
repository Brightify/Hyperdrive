package org.brightify.hyperdrive.krpc.client.impl.ktor

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.client.RPCClientConnector
import org.brightify.hyperdrive.krpc.ktor.WebSocketSessionConnection
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class WebSocketClient(
    private val host: String = "localhost",
    private val port: Int = 8000,
    private val path: String = "/",
    private val useSecureConnection: Boolean = false,
    clientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = ::HttpClient,
) : RPCClientConnector {

    private val httpClient = clientFactory {
        install(WebSockets)
    }

    private var activeConnection: WebSocketSessionConnection? = null
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

    private suspend fun connect(): WebSocketSessionConnection {
        return connectingMutex.withLock {
            val oldConnection = activeConnection
            if (oldConnection != null && oldConnection.isActive) {
                oldConnection.close()
            }
            val webSocketSession = httpClient.webSocketSession(host = host, port = port, path = path) {
                if (useSecureConnection) {
                    url { protocol = URLProtocol.WSS }
                }
            }
            val newConnection = WebSocketSessionConnection(webSocketSession)
            activeConnection = newConnection
            return@withLock newConnection
        }
    }
}
