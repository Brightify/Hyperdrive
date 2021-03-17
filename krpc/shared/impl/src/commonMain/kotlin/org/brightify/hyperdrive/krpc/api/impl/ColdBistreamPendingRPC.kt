package org.brightify.hyperdrive.krpc.api.impl

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdBistreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.DownstreamRPCEvent
import org.brightify.hyperdrive.krpc.api.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.api.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.api.RPCConnection
import org.brightify.hyperdrive.krpc.api.RPCFrame
import org.brightify.hyperdrive.krpc.api.RPCReference
import org.brightify.hyperdrive.krpc.api.UpstreamRPCEvent

object ColdBistreamPendingRPC {
    private val <REQUEST, CLIENT_STREAM, SERVER_STREAM> CallDescriptor.ColdBistream<REQUEST, CLIENT_STREAM, SERVER_STREAM>.clientStreamEventSerializer: KSerializer<out StreamEvent<out CLIENT_STREAM>>
        get() = StreamEventSerializer(clientStreamSerializer, errorSerializer)

    private val <REQUEST, CLIENT_STREAM, SERVER_STREAM> ColdBistreamCallDescriptor<REQUEST, CLIENT_STREAM, SERVER_STREAM>.clientStreamEventSerializer: KSerializer<out StreamEvent<out CLIENT_STREAM>>
        get() = StreamEventSerializer(clientStreamSerializer, errorSerializer)

    private val <REQUEST, CLIENT_STREAM, SERVER_STREAM> CallDescriptor.ColdBistream<REQUEST, CLIENT_STREAM, SERVER_STREAM>.serverStreamEventSerializer: KSerializer<out StreamEvent<out SERVER_STREAM>>
        get() = StreamEventSerializer(responseSerializer, errorSerializer)

    private val <REQUEST, CLIENT_STREAM, SERVER_STREAM> ColdBistreamCallDescriptor<REQUEST, CLIENT_STREAM, SERVER_STREAM>.serverStreamEventSerializer: KSerializer<out StreamEvent<out SERVER_STREAM>>
        get() = StreamEventSerializer(serverStreamSerializer, errorSerializer)

    class Server<REQUEST, CLIENT_STREAM, SERVER_STREAM>(
        connection: RPCConnection,
        reference: RPCReference,
        call: CallDescriptor.ColdBistream<REQUEST, CLIENT_STREAM, SERVER_STREAM>,
        onFinished: () -> Unit,
    ): _PendingRPC.Server<REQUEST, CallDescriptor.ColdBistream<REQUEST, CLIENT_STREAM, SERVER_STREAM>>(connection, reference, call, onFinished) {

        private var isClosed = false
        private val channel = Channel<CLIENT_STREAM>()

        override suspend fun handle(frame: IncomingRPCFrame<UpstreamRPCEvent>) {
            Do exhaustive when (frame.header.event) {
                is UpstreamRPCEvent.Open -> run {
                    val data = frame.decoder.decodeSerializableValue(call.requestSerializer)

                    val clientStreamFlow = channel.consumeAsFlow()
                        .onStart {
                            frame.confirmOpened()
                        }
                        .onCompletion { exception ->
                            if (exception == null) {
                                frame.closeClientStream()
                            }
                        }

                    val flow = call.perform(data, clientStreamFlow)

                    flow
                        .takeWhile {
                            !isClosed
                        }
                        .catch {
                            if (it !is CancellationException) {
                                frame.sendStreamEvent(StreamEvent.Error(it))
                            }
                        }
                        .collect {
                            frame.sendStreamEvent(StreamEvent.Next(it))
                        }

                    frame.sendStreamEvent(StreamEvent.Complete())
                }
                UpstreamRPCEvent.Data -> {
                    val event = frame.decoder.decodeSerializableValue(call.clientStreamEventSerializer)

                    Do exhaustive when (event) {
                        is StreamEvent.Next -> channel.send(event.data)
                        is StreamEvent.Complete -> channel.close()
                        is StreamEvent.Error -> channel.close(event.error)
                    }
                }
                is UpstreamRPCEvent.StreamOperation -> {
                    isClosed = true
                }
                UpstreamRPCEvent.Warning -> {
                    val error = frame.decoder.decodeSerializableValue(call.errorSerializer)
                    // TODO: Log me!
                    println("Warning form client: $error")
                }
                UpstreamRPCEvent.Error -> {
                    val error = frame.decoder.decodeSerializableValue(call.errorSerializer)
                    cancel("Error received from the client.", error)
                }
                UpstreamRPCEvent.Cancel -> {
                    cancel("Cancellation from the client.")
                }
            }
        }

        private suspend fun IncomingRPCFrame<UpstreamRPCEvent>.confirmOpened() {
            connection.send(OutgoingRPCFrame(
                RPCFrame.Header(header.callReference, DownstreamRPCEvent.Opened),
                Unit.serializer(),
                Unit,
            ))
        }

        private suspend fun IncomingRPCFrame<UpstreamRPCEvent>.closeClientStream() {
            connection.send(OutgoingRPCFrame(
                RPCFrame.Header(header.callReference, DownstreamRPCEvent.StreamOperation.Close),
                Unit.serializer(),
                Unit,
            ))
        }

        private suspend fun IncomingRPCFrame<UpstreamRPCEvent>.sendStreamEvent(event: StreamEvent<SERVER_STREAM>) {
            connection.send(OutgoingRPCFrame(
                RPCFrame.Header(header.callReference, DownstreamRPCEvent.Data),
                call.serverStreamEventSerializer,
                event,
            ))
        }
    }

    class Client<REQUEST, CLIENT_STREAM, SERVER_STREAM>(
        connection: RPCConnection,
        reference: RPCReference,
        call: ColdBistreamCallDescriptor<REQUEST, CLIENT_STREAM, SERVER_STREAM>,
        private val clientStream: Flow<CLIENT_STREAM>,
        onFinished: () -> Unit,
    ): _PendingRPC.Client<REQUEST, Flow<SERVER_STREAM>, ColdBistreamCallDescriptor<REQUEST, CLIENT_STREAM, SERVER_STREAM>>(connection, reference, call, onFinished) {

        private var isClosed: Boolean = false
        private val incomingChannelDeferred = CompletableDeferred<Channel<SERVER_STREAM>>()

        override suspend fun perform(payload: REQUEST): Flow<SERVER_STREAM> {
            open(payload)

            return incomingChannelDeferred.await().receiveAsFlow()
        }

        override suspend fun handle(frame: IncomingRPCFrame<DownstreamRPCEvent>) {
            Do exhaustive when (frame.header.event) {
                DownstreamRPCEvent.Opened -> {
                    incomingChannelDeferred.complete(Channel())
                    connection.launch {
                        try {
                            clientStream.collect {
                                sendStreamEvent(StreamEvent.Next(it))
                            }

                            sendStreamEvent(StreamEvent.Complete())
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            sendStreamEvent(StreamEvent.Error(t))
                        }
                    }
                }
                DownstreamRPCEvent.Data -> {
                    if (incomingChannelDeferred.isCompleted) {
                        val event = frame.decoder.decodeSerializableValue(call.serverStreamEventSerializer)
                        val channel = incomingChannelDeferred.getCompleted()
                        Do exhaustive when (event) {
                            is StreamEvent.Next -> channel.send(event.data)
                            is StreamEvent.Complete -> channel.close()
                            is StreamEvent.Error -> channel.close(event.error)
                        }
                    } else {
                        // frame.rejectAsUnexpected("Channel wasn't open. Has to be open first!")
                        cancel("Channel wasn't open. Has to be open first!")
                    }
                }
                DownstreamRPCEvent.Response -> {
                    // This might have been an ColdUpstream channel before, so we'll accept the sent data.
                    frame.warnUnexpected()

                    val payload = frame.decoder.decodeSerializableValue(call.serverStreamSerializer)
                    val channel = if (incomingChannelDeferred.isCompleted) incomingChannelDeferred.getCompleted() else Channel<SERVER_STREAM>().also { incomingChannelDeferred.complete(it) }
                    channel.send(payload)
                    channel.close()
                }
                is DownstreamRPCEvent.StreamOperation -> {
                    isClosed = true
                }
                DownstreamRPCEvent.Warning -> TODO()
                DownstreamRPCEvent.Error -> {
                    val error = frame.decoder.decodeSerializableValue(errorSerializer)
                    cancel("Received an error from server.", error)
                }
            }
        }

        private suspend fun sendStreamEvent(event: StreamEvent<CLIENT_STREAM>) {
            connection.send(OutgoingRPCFrame(
                RPCFrame.Header(reference, UpstreamRPCEvent.Data),
                call.clientStreamEventSerializer,
                event,
            ))
        }
    }
}