package org.brightify.hyperdrive.krpc.server.impl

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.serializer
import org.brightify.hyperdrive.krpc.api.*
import org.brightify.hyperdrive.krpc.api.error.NonRPCErrorThrownError
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.server.api.Server
import kotlin.reflect.KClass

class DefaultServiceRegistry: ServiceRegistry {
    private val services: MutableMap<String, ServiceDescription> = mutableMapOf()
    private val serviceCalls: MutableMap<String, Map<String, CallDescriptor>> = mutableMapOf()

    override fun register(description: ServiceDescription) {
        services[description.identifier] = description

        serviceCalls[description.identifier] = description.calls.map {
            it.identifier.callId to it
        }.toMap()
    }

    override fun <T: CallDescriptor> getCallById(id: ServiceCallIdentifier, type: KClass<T>): T? {
        val knownCall = serviceCalls[id.serviceId]?.get(id.callId) ?: return null
        return if (type.isInstance(knownCall)) {
            knownCall as T
        } else {
            null
        }
    }
}

interface ServiceRegistry {
    fun register(description: ServiceDescription)

    fun <T: CallDescriptor> getCallById(id: ServiceCallIdentifier, type: KClass<T>): T?
}

private typealias ClientReference = Int

class KtorServer(
//    val pingService: PingService
    val host: String = "0.0.0.0",
    val port: Int = 8000,
    val frameConverter: WebSocketFrameConverter<Frame, RPCEvent.Downstream, RPCEvent.Upstream>,
    val serviceRegistry: ServiceRegistry,
    val responseScope: CoroutineScope
): Server {
    private val openStreams = mutableMapOf<ClientReference, MutableMap<RPCReference, Stream>>()
    private val baseRPCErrorSerializer = RPCErrorSerializer()

    private var clientReferenceCounter = Int.MIN_VALUE

    private class Stream(
        private val channel: Channel<Any?>,
        private val dataSerializer: DeserializationStrategy<Any?>,
        private val errorSerializer: RPCErrorSerializer
    ) {
        suspend fun accept(decoder: Decoder) {
            val data = decoder.decodeSerializableValue(dataSerializer)
            channel.send(data)
        }

        suspend fun reject(decoder: Decoder) {
            val throwable = decoder.decodeSerializableValue(errorSerializer)
            channel.close(throwable)
        }

        suspend fun close() {
            channel.close()
        }
    }

    private val engine: NettyApplicationEngine = embeddedServer(Netty, port = port, host = host) {
        routing {
            install(WebSockets)

            get("/") {
                call.respondText("<h1>kRPC</h1>", ContentType.Text.Html)
            }

            webSocket("/") {
                val clientReference = nextClientReference()
                try {
                    while (true) {
                        val frame = incoming.receive()
                        val rpcFrame = frameConverter.rpcFrameFromWebSocketFrame(frame)

                        suspend fun IncomingRPCFrame<RPCEvent.Upstream>.respond(
                            event: RPCEvent.Downstream,
                            serializer: KSerializer<out Any?>,
                            data: Any?,
                        ) = send(
                            frameConverter.rpcFrameToWebSocketFrame(
                                OutgoingRPCFrame(
                                    header = RPCFrame.Header(
                                        this.header.callReference,
                                        event,
                                    ),
                                    serializationStrategy = serializer as SerializationStrategy<Any?>,
                                    data,
                                )
                            )
                        )

                        println("Server - $rpcFrame")

                        when (val event = rpcFrame.header.event) {
                            is RPCEvent.Upstream.Open -> {
                                val call = serviceRegistry.getCallById(event.serviceCall, CallDescriptor::class)
                                responseScope.launch {
                                    val exhaustive = when (call) {
                                        is CallDescriptor.Single<*, *> -> {
                                            val call = call as CallDescriptor.Single<Any?, Any?>
                                            val data = rpcFrame.decoder.decodeSerializableValue(call.requestSerializer)
                                            try {
                                                rpcFrame.respond(
                                                    RPCEvent.Downstream.Response,
                                                    call.responseSerializer,
                                                    call.perform(data),
                                                )
                                            } catch (t: Throwable) {
                                                rpcFrame.respond(
                                                    RPCEvent.Downstream.Error,
                                                    call.errorSerializer,
                                                    t,
                                                )
                                            }
                                        }
                                        is CallDescriptor.ColdUpstream<*, *, *> -> {
                                            val call = call as CallDescriptor.ColdUpstream<Any?, Any?, Any?>
                                            val data =
                                                rpcFrame.decoder.decodeSerializableValue(call.requestSerializer as DeserializationStrategy<Any?>)
                                            val channel = Channel<Any?>()

                                            openStreams.getOrPut(clientReference, ::mutableMapOf)[rpcFrame.header.callReference] = Stream(
                                                channel, call.clientStreamSerializer as DeserializationStrategy<Any?>, call.errorSerializer
                                            )

                                            try {
                                                rpcFrame.respond(
                                                    RPCEvent.Downstream.Response,
                                                    call.responseSerializer,
                                                    call.perform(data, channel.consumeAsFlow()
                                                        .onStart {
                                                            rpcFrame.respond(
                                                                RPCEvent.Downstream.Opened,
                                                                Unit.serializer(),
                                                                Unit,
                                                            )
                                                        }
                                                        .onCompletion {
                                                            rpcFrame.respond(
                                                                RPCEvent.Downstream.Close,
                                                                Unit.serializer(),
                                                                Unit,
                                                            )
                                                        }),
                                                )
                                            } catch (e: CancellationException) {
                                                e.printStackTrace()
                                            } catch (t: Throwable) {
                                                rpcFrame.respond(
                                                    RPCEvent.Downstream.Error,
                                                    call.errorSerializer,
                                                    t,
                                                )
                                            }
                                        }
                                        is CallDescriptor.ColdDownstream<*, *> -> {
                                            val call = call as CallDescriptor.ColdDownstream<Any?, Any?>
                                            val data =
                                                rpcFrame.decoder.decodeSerializableValue(call.requestSerializer as DeserializationStrategy<Any?>)

                                            try {
                                                val flow = call.perform(data)

                                                rpcFrame.respond(
                                                    RPCEvent.Downstream.Opened,
                                                    Unit.serializer(),
                                                    Unit,
                                                )

                                                flow
                                                    .onCompletion {
                                                        rpcFrame.respond(
                                                            RPCEvent.Downstream.Close,
                                                            Unit.serializer(),
                                                            Unit,
                                                        )
                                                    }
                                                    .collect { data ->
                                                        rpcFrame.respond(
                                                            RPCEvent.Downstream.Data,
                                                            call.responseSerializer as KSerializer<Any?>,
                                                            data,
                                                        )
                                                    }
                                            } catch (t: Throwable) {
                                                rpcFrame.respond(
                                                    RPCEvent.Downstream.Error,
                                                    call.errorSerializer,
                                                    t,
                                                )
                                            }
                                        }
                                        is CallDescriptor.ColdBistream<*, *, *> -> {
                                            val call = call as CallDescriptor.ColdBistream<Any?, Any?, Any?>
                                            val data = rpcFrame.decoder.decodeSerializableValue(call.requestSerializer as DeserializationStrategy<Any?>)
                                            val channel = Channel<Any?>()

                                            openStreams.getOrPut(clientReference, ::mutableMapOf)[rpcFrame.header.callReference] = Stream(
                                                channel, call.clientStreamSerializer as DeserializationStrategy<Any?>, call.errorSerializer
                                            )

                                            try {
                                                val flow = call.perform(data, channel.consumeAsFlow()
                                                    .onStart {
                                                        rpcFrame.respond(
                                                            RPCEvent.Downstream.Opened,
                                                            Unit.serializer(),
                                                            Unit,
                                                        )
                                                    }
                                                    .onCompletion {
                                                        rpcFrame.respond(
                                                            RPCEvent.Downstream.Close,
                                                            Unit.serializer(),
                                                            Unit,
                                                        )
                                                    })

                                                flow
                                                    .onCompletion {
                                                        rpcFrame.respond(
                                                            RPCEvent.Downstream.Close,
                                                            Unit.serializer(),
                                                            Unit,
                                                        )
                                                    }
                                                    .collect { data ->
                                                        rpcFrame.respond(
                                                            RPCEvent.Downstream.Data,
                                                            call.responseSerializer as KSerializer<Any?>,
                                                            data,
                                                        )
                                                    }
                                            } catch (e: CancellationException) {
                                                e.printStackTrace()
                                            } catch (t: Throwable) {
                                                rpcFrame.respond(
                                                    RPCEvent.Downstream.Error,
                                                    call.errorSerializer,
                                                    t,
                                                )
                                            }
                                        }
                                        null -> rpcFrame.respond(
                                            RPCEvent.Downstream.Error,
                                            baseRPCErrorSerializer,
                                            NotImplementedError("Not implemented!"),
                                        )
                                    }
                                }
                            }
                            is RPCEvent.Upstream.Data -> {
                                // TODO: Send error if we can't find the reference
                                responseScope.launch {
                                    openStreams[clientReference]?.get(rpcFrame.header.callReference)?.accept(rpcFrame.decoder)
                                        ?: error("Call reference doesn't exist!")
                                }
                            }
                            is RPCEvent.Upstream.Error -> {
                                // TODO: Send error if we can't find the reference
                                openStreams[clientReference]?.get(rpcFrame.header.callReference)?.reject(rpcFrame.decoder)
                                openStreams[clientReference]?.remove(rpcFrame.header.callReference)
                            }
                            is RPCEvent.Upstream.Close -> {
                                // TODO: Send error if we can't find the reference
                                openStreams[clientReference]?.get(rpcFrame.header.callReference)?.close()
                                openStreams[clientReference]?.remove(rpcFrame.header.callReference)
                            }
                        }

//                        when (val event = rpcFrame.header.event) {
//                            is RPCEvent.Upstream.SingleCall.Request -> {
//                                val call = serviceRegistry.getCallById(event.serviceCall, CallDescriptor.Single::class) as CallDescriptor.Single<Any?, Any?>?
//                                responseScope.launch {
//                                    send(
//                                        frameConverter.rpcFrameToWebSocketFrame(
//                                            if (call != null) {
//                                                val data = rpcFrame.decoder.decodeSerializableValue(call.requestSerializer as DeserializationStrategy<Any?>)
//                                                try {
//                                                    OutgoingRPCFrame(
//                                                        RPCFrame.Header(
//                                                            rpcFrame.header.callReference,
//                                                            RPCEvent.Downstream.SingleCall.Response
//                                                        ),
//                                                        call.responseSerializer as SerializationStrategy<Any?>,
//                                                        call.perform(data)
//                                                    )
//                                                } catch (t: Throwable) {
//                                                    OutgoingRPCFrame(
//                                                        RPCFrame.Header(
//                                                            rpcFrame.header.callReference,
//                                                            RPCEvent.Downstream.SingleCall.Error
//                                                        ),
//                                                        serializer<Throwable>() as SerializationStrategy<Any?>,
//                                                        t
//                                                    )
//                                                }
//                                            } else {
//                                                OutgoingRPCFrame(
//                                                    RPCFrame.Header(
//                                                        rpcFrame.header.callReference,
//                                                        RPCEvent.Downstream.SingleCall.Error
//                                                    ),
//                                                    serializer<NotImplementedError>() as SerializationStrategy<Any?>,
//                                                    NotImplementedError("Not implemented!")
//                                                )
//                                            }
//                                        )
//                                    )
//                                }
//                            }
//                            is RPCEvent.Upstream.OutStream.Open -> {
//                                val call = serviceRegistry.getCallById(event.serviceCall, CallDescriptor.ColdUpstream::class) as CallDescriptor.ColdUpstream<Any?, Any?, Any?>?
//
//                                responseScope.launch {
//                                    if (call != null) {
//                                        val data = rpcFrame.decoder.decodeSerializableValue(call.requestSerializer as DeserializationStrategy<Any?>)
//                                        val channel = Channel<Decoder>()
//                                        openStreams.getOrPut(clientReference, ::mutableMapOf)[rpcFrame.header.callReference] = channel
//                                        send(
//                                            frameConverter.rpcFrameToWebSocketFrame(
//                                                try {
//                                                    OutgoingRPCFrame(
//                                                        RPCFrame.Header(
//                                                            rpcFrame.header.callReference,
//                                                            RPCEvent.Downstream.SingleCall.Response
//                                                        ),
//                                                        call.responseSerializer as SerializationStrategy<Any?>,
//                                                        call.perform(data, channel.consumeAsFlow().map {
//                                                            it.decodeSerializableValue(call.clientStreamSerializer as DeserializationStrategy<Any>)
//                                                        })
//                                                    )
//                                                } catch (t: Throwable) {
//                                                    OutgoingRPCFrame(
//                                                        RPCFrame.Header(
//                                                            rpcFrame.header.callReference,
//                                                            RPCEvent.Downstream.SingleCall.Error
//                                                        ),
//                                                        serializer<Throwable>() as SerializationStrategy<Any?>,
//                                                        t
//                                                    )
//                                                }
//                                            )
//                                        )
//                                    } else {
//                                        send(
//                                            frameConverter.rpcFrameToWebSocketFrame(
//                                                OutgoingRPCFrame(
//                                                    RPCFrame.Header(
//                                                        rpcFrame.header.callReference,
//                                                        RPCEvent.Downstream.OutStream.Error
//                                                    ),
//                                                    serializer<NotImplementedError>() as SerializationStrategy<Any?>,
//                                                    NotImplementedError("Not implemented!")
//                                                )
//                                            )
//                                        )
//                                    }
//                                }
//                            }
//                            is RPCEvent.Upstream.OutStream.SendUpstream -> {
//                                // TODO: Send error if we can't find the reference
//                                responseScope.launch {
//                                    openStreams[clientReference]?.get(rpcFrame.header.callReference)?.send(rpcFrame.decoder)
//                                        ?: error("Call reference doesn't exist!")
//                                }
//                            }
//                            is RPCEvent.Upstream.OutStream.Close -> {
//                                openStreams[clientReference]?.get(rpcFrame.header.callReference)?.close()
//                                openStreams[clientReference]?.remove(rpcFrame.header.callReference)
//                            }
//                            is RPCEvent.Upstream.InStream.Open -> {
//                                val call = serviceRegistry.getCallById(event.serviceCall, CallDescriptor.ColdDownstream::class) as CallDescriptor.ColdDownstream<Any?, Any?>?
//                                responseScope.launch {
//                                    if (call != null) {
//                                        val data = rpcFrame.decoder.decodeSerializableValue(call.requestSerializer as DeserializationStrategy<Any?>)
//                                        val channel = Channel<Decoder>()
//                                        openStreams.getOrPut(clientReference, ::mutableMapOf)[rpcFrame.header.callReference] = channel
//                                        send(
//                                            frameConverter.rpcFrameToWebSocketFrame(
//                                                try {
//                                                    OutgoingRPCFrame(
//                                                        RPCFrame.Header(
//                                                            rpcFrame.header.callReference,
//                                                            RPCEvent.Downstream.SingleCall.Response
//                                                        ),
//                                                        call.responseSerializer as SerializationStrategy<Any?>,
//                                                        call.perform(data, channel.consumeAsFlow().map {
//                                                            it.decodeSerializableValue(call.clientStreamSerializer as DeserializationStrategy<Any>)
//                                                        })
//                                                    )
//                                                } catch (t: Throwable) {
//                                                    OutgoingRPCFrame(
//                                                        RPCFrame.Header(
//                                                            rpcFrame.header.callReference,
//                                                            RPCEvent.Downstream.SingleCall.Error
//                                                        ),
//                                                        serializer<Throwable>() as SerializationStrategy<Any?>,
//                                                        t
//                                                    )
//                                                }
//                                            )
//                                        )
//                                    } else {
//                                        send(
//                                            frameConverter.rpcFrameToWebSocketFrame(
//                                                OutgoingRPCFrame(
//                                                    RPCFrame.Header(
//                                                        rpcFrame.header.callReference,
//                                                        RPCEvent.Downstream.OutStream.Error
//                                                    ),
//                                                    serializer<NotImplementedError>() as SerializationStrategy<Any?>,
//                                                    NotImplementedError("Not implemented!")
//                                                )
//                                            )
//                                        )
//                                    }
//                                }
//
//                                responseScope.launch {
//
//
//                                    val flow = call.perform(data)
//
//
//                                    send(
//                                        frameConverter.rpcFrameToWebSocketFrame(
//                                            if (call != null) {
//                                                val data = rpcFrame.decoder.decodeSerializableValue(call.requestSerializer as DeserializationStrategy<Any?>)
//                                                try {
//                                                    OutgoingRPCFrame(
//                                                        RPCFrame.Header(
//                                                            rpcFrame.header.callReference,
//                                                            RPCEvent.Downstream.SingleCall.Response
//                                                        ),
//                                                        call.responseSerializer as SerializationStrategy<Any?>,
//                                                        call.perform(data)
//                                                    )
//                                                } catch (t: Throwable) {
//                                                    OutgoingRPCFrame(
//                                                        RPCFrame.Header(
//                                                            rpcFrame.header.callReference,
//                                                            RPCEvent.Downstream.SingleCall.Error
//                                                        ),
//                                                        serializer<Throwable>() as SerializationStrategy<Any?>,
//                                                        t
//                                                    )
//                                                }
//                                            } else {
//                                                OutgoingRPCFrame(
//                                                    RPCFrame.Header(
//                                                        rpcFrame.header.callReference,
//                                                        RPCEvent.Downstream.SingleCall.Error
//                                                    ),
//                                                    serializer<NotImplementedError>() as SerializationStrategy<Any?>,
//                                                    NotImplementedError("Not implemented!")
//                                                )
//                                            }
//                                        )
//                                    )
//                                }
//                            }
//                        }
                    }
                } catch (e: ClosedReceiveChannelException) {
                    // Client disconnected, clean up and do nothing.
                } catch (t: Throwable) {
                    t.printStackTrace()
                    throw t
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

    private fun nextClientReference(): Int {
        return clientReferenceCounter++
    }
}