package org.brightify.hyperdrive.krpc.server.impl.ktor

import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import org.brightify.hyperdrive.krpc.ServiceRegistry
import org.brightify.hyperdrive.krpc.frame.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.frame.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.frame.RPCEvent
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import org.brightify.hyperdrive.krpc.api.WebSocketFrameConverter
import org.brightify.hyperdrive.krpc.server.api.Server
import org.brightify.hyperdrive.krpc.server.impl.KRPCServer

class KtorServerFrontend(
    host: String = "0.0.0.0",
    port: Int = 8000,
    val frameConverter: WebSocketFrameConverter<Frame>,
    val serviceRegistry: ServiceRegistry,
): Server {
    private val server = KRPCServer(serviceRegistry)

    private val engine: NettyApplicationEngine = embeddedServer(Netty, port = port, host = host) {
        routing {
            install(WebSockets)

            webSocket("/") {
                val connection = Connection(this)
                server.handleNewConnection(connection)
//                server.handleNewClient(
//                    sendOutgoing = {
//                        send(frameConverter.rpcFrameToWebSocketFrame(it))
//                    },
//                    handler = {
//                        try {
//                            val frame = incoming.receive()
//                            frameConverter.rpcFrameFromWebSocketFrame(frame)
//                        } catch (e: ClosedReceiveChannelException) {
//                            // Client disconnected, clean up and do nothing.
//                            throw CancellationException(e)
//                        } catch (t: Throwable) {
//                            t.printStackTrace()
//                            throw t
//                        }
//                    })
            }
        }
    }.start(wait = false)

    override fun register(description: ServiceDescription) {
        serviceRegistry.register(description)
    }

    fun shutdown(gracePeriodMillis: Long = 0, timeoutMillis: Long = 30) {
        engine.stop(gracePeriodMillis, timeoutMillis)
    }

    private inner class Connection(val webSocketSession: DefaultWebSocketSession): RPCConnection, CoroutineScope by webSocketSession {
        override suspend fun receive(): IncomingRPCFrame<RPCEvent> {
            val frame = webSocketSession.incoming.receive()
            return frameConverter.rpcFrameFromWebSocketFrame(frame)
        }

        override suspend fun send(frame: OutgoingRPCFrame<RPCEvent>) {
            webSocketSession.outgoing.send(frameConverter.rpcFrameToWebSocketFrame(frame))
        }

        override suspend fun close() {
            webSocketSession.close()
        }
    }
}