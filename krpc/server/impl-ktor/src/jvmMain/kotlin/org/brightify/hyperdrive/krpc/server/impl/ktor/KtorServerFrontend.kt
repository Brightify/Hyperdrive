package org.brightify.hyperdrive.krpc.server.impl.ktor

import io.ktor.application.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import org.brightify.hyperdrive.krpc.ktor.WebSocketSessionConnection
import org.brightify.hyperdrive.krpc.server.impl.ProvidingServerConnector

class KtorServerFrontend(
    host: String = "0.0.0.0",
    port: Int = 8000,
): ProvidingServerConnector() {
    private val engine: NettyApplicationEngine = embeddedServer(Netty, port = port, host = host) {
        routing {
            install(WebSockets)

            webSocket("/") {
                val connection = WebSocketSessionConnection(this)
                provide(connection)
            }
        }
    }.start(wait = false)

    fun shutdown(gracePeriodMillis: Long = 0, timeoutMillis: Long = 30) {
        engine.stop(gracePeriodMillis, timeoutMillis)
    }
}