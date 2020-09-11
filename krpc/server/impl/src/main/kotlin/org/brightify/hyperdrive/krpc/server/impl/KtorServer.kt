package org.brightify.hyperdrive.krpc.server.impl

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.brightify.hyperdrive.krpc.PingService
import org.brightify.hyperdrive.krpc.api.*
import org.brightify.hyperdrive.krpc.server.api.Server

internal class SerializerResolvingServiceRegistry: ServiceRegistry, RPCSerializerResolver {
    private val services: MutableMap<String, ServiceDescription> = mutableMapOf()
    private val serviceCalls: MutableMap<String, Map<String, CallDescriptor>> = mutableMapOf()

    override fun register(description: ServiceDescription) {
        services[description.identifier] = description

        serviceCalls[description.identifier] = description.calls.map {
            it.identifier to it
        }.toMap()
    }

    override fun getCallById(id: ServiceCallIdentifier): CallDescriptor? {
        return serviceCalls[id.serviceId]?.get(id.callId)
    }

    override fun serializerFor(header: RPCFrame.Header<*>): KSerializer<Any?>? {
        return when (val event = header.event) {
            is RPCEvent.Upstream.SingleCall.Request -> {
                val call = getCallById(event.serviceCall) ?: return null

                serializer(call.requestType)
            }
            is RPCEvent.Upstream.OutStream.Open -> TODO()
            is RPCEvent.Upstream.OutStream.SendUpstream -> TODO()
            is RPCEvent.Upstream.OutStream.Close -> TODO()
            is RPCEvent.Upstream.InStream.Open -> TODO()
            is RPCEvent.Downstream.SingleCall.Response -> {
                val call = getCallById(event.) ?: return null

                serializer(call.requestType)
            }
            is RPCEvent.Downstream.SingleCall.Error -> TODO()
            is RPCEvent.Downstream.ClientStream.Response -> TODO()
            is RPCEvent.Downstream.ClientStream.Error -> TODO()
            is RPCEvent.Downstream.ServerStream.SendDownstream -> TODO()
            is RPCEvent.Downstream.ServerStream.Close -> TODO()
            is RPCEvent.Downstream.BidiStream.SendDownstream -> TODO()
            is RPCEvent.Downstream.BidiStream.Close -> TODO()
        }
    }
}

interface ServiceRegistry {
    fun register(description: ServiceDescription)

    fun getCallById(id: ServiceCallIdentifier): CallDescriptor?
}

class KtorServer(
//    val pingService: PingService
    val host: String = "0.0.0.0",
    val port: Int = 8000,
    val frameConverter: WebSocketFrameConverter<Frame>,
    val serviceRegistry: ServiceRegistry
): Server {

    private val engine: NettyApplicationEngine = embeddedServer(Netty, port = port, host = host) {
        routing {
            install(WebSockets)

            get("/") {
                call.respondText("<h1>kRPC</h1>", ContentType.Text.Html)
            }

            webSocket("/") {
                try {
                    while (true) {
                        val frame = incoming.receive()
                        val rpcFrame = frameConverter.upstreamFrameFromWebSocketFrame(frame) { header ->

                        }
                        println(rpcFrame)

                        when (val event = rpcFrame.header.event) {
                            is RPCEvent.Upstream.SingleCall.Request -> {
                                val call = serviceRegistry.getCallById(event.serviceCall)
                                send(
                                    frameConverter.downstreamFrameToWebSocketFrame(
                                        if (call != null) {
                                            try {
                                                RPCFrame(
                                                    RPCFrame.Header(
                                                        rpcFrame.header.index + 1,
                                                        RPCEvent.Downstream.SingleCall.Response(rpcFrame.header.index)
                                                    ),
                                                    call.perform(rpcFrame.data)
                                                )
                                            } catch (t: Throwable) {
                                                RPCFrame(
                                                    RPCFrame.Header(
                                                        rpcFrame.header.index + 1,
                                                        RPCEvent.Downstream.SingleCall.Error(rpcFrame.header.index)
                                                    ),
                                                    t
                                                )
                                            }
                                        } else {
                                            RPCFrame(
                                                RPCFrame.Header(
                                                    rpcFrame.header.index + 1,
                                                    RPCEvent.Downstream.SingleCall.Error(rpcFrame.header.index)
                                                ),
                                                NotImplementedError("Not implemented!")
                                            )
                                        }
                                    )
                                )
                            }
                            is RPCEvent.Upstream.OutStream.Open -> TODO()
                            is RPCEvent.Upstream.OutStream.SendUpstream -> TODO()
                            is RPCEvent.Upstream.OutStream.Close -> TODO()
                            is RPCEvent.Upstream.InStream.Open -> TODO()
                        }
                    }
                } catch (e: ClosedReceiveChannelException) {
                    // Client disconnected, clean up and do nothing.
                }
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