package org.brightify.hyperdrive.krpc.server.impl

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.api.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.api.RPCEvent
import org.brightify.hyperdrive.krpc.api.RPCFrame
import org.brightify.hyperdrive.krpc.api.RPCReference
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

class KRPCServer(
    val serviceRegistry: ServiceRegistry,
    val responseScope: CoroutineScope,
) {

    private val openStreams = mutableMapOf<ClientReference, MutableMap<RPCReference, Stream>>()
    private val baseRPCErrorSerializer = RPCErrorSerializer()

    private var clientReferenceCounter = Int.MIN_VALUE

    suspend fun handleNewClient(
        sendOutgoing: suspend (OutgoingRPCFrame<RPCEvent.Downstream>) -> Unit,
        handler: suspend (suspend (IncomingRPCFrame<RPCEvent.Upstream>) -> Unit) -> Unit
    ) {
        val clientReference = nextClientReference()

        suspend fun IncomingRPCFrame<RPCEvent.Upstream>.respond(
            event: RPCEvent.Downstream,
            serializer: KSerializer<out Any?>,
            data: Any?,
        ) = sendOutgoing(
            OutgoingRPCFrame(
                header = RPCFrame.Header(
                    this.header.callReference,
                    event,
                ),
                serializationStrategy = serializer as SerializationStrategy<Any?>,
                data,
            )
        )

        handler { rpcFrame ->
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
        }
    }

    private fun nextClientReference(): Int {
        return clientReferenceCounter++
    }

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
}