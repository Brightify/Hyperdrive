package org.brightify.hyperdrive.krpc.server.impl

import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import org.brightify.hyperdrive.krpc.api.RPCEvent
import org.brightify.hyperdrive.krpc.api.ServiceDescription
import org.brightify.hyperdrive.krpc.api.WebSocketFrameConverter
import org.brightify.hyperdrive.krpc.server.api.Server

class KtorServerFrontend(
    host: String = "0.0.0.0",
    port: Int = 8000,
    val frameConverter: WebSocketFrameConverter<Frame, RPCEvent.Downstream, RPCEvent.Upstream>,
    val serviceRegistry: ServiceRegistry,
    val responseScope: CoroutineScope
): Server {
    private val server = KRPCServer(serviceRegistry, responseScope)

    private val engine: NettyApplicationEngine = embeddedServer(Netty, port = port, host = host) {
        routing {
            install(WebSockets)

            webSocket("/") {
                server.handleNewClient(
                    sendOutgoing = {
                        send(frameConverter.rpcFrameToWebSocketFrame(it))
                    },
                    handler = { handleReceive ->
                        try {
                            while (isActive) {
                                val frame = incoming.receive()
                                val rpcFrame = frameConverter.rpcFrameFromWebSocketFrame(frame)
                                handleReceive(rpcFrame)
                            }
                        } catch (e: ClosedReceiveChannelException) {
                            // Client disconnected, clean up and do nothing.
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            throw t
                        }
                    })
            }
        }
    }.start(wait = false)

    override fun register(description: ServiceDescription) {
        serviceRegistry.register(description)
    }

    fun shutdown(gracePeriodMillis: Long = 0, timeoutMillis: Long = 30) {
        engine.stop(gracePeriodMillis, timeoutMillis)
    }
}