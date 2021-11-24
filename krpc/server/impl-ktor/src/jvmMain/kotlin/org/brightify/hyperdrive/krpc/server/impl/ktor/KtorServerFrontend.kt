package org.brightify.hyperdrive.krpc.server.impl.ktor

import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.brightify.hyperdrive.krpc.ktor.WebSocketSessionConnection
import org.brightify.hyperdrive.krpc.server.impl.ProvidingServerConnector
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class KtorServerFrontend @OptIn(ExperimentalTime::class) constructor(
    host: String = "0.0.0.0",
    port: Int = 8000,
    heartBeatInterval: Duration? = null,
): ProvidingServerConnector() {
    private val engine: NettyApplicationEngine = embeddedServer(Netty, port = port, host = host) {
        routing {
            install(WebSockets)

            webSocket("/") {
                val connection = WebSocketSessionConnection(this)
                heartBeatInterval?.let { interval ->
                    launch {
                        @OptIn(ExperimentalTime::class)
                        delay(interval)
                        // TODO: We should check if we receive pongs from the client. Now we do this to make sure the network between the
                        //  client and the server isn't dropping the connection due to inactivity.
                        send(Frame.Ping(data = byteArrayOf()))
                    }
                }
                provide(connection)
            }
        }
    }.start(wait = false)

    fun shutdown(gracePeriodMillis: Long = 0, timeoutMillis: Long = 30) {
        engine.stop(gracePeriodMillis, timeoutMillis)
    }
}