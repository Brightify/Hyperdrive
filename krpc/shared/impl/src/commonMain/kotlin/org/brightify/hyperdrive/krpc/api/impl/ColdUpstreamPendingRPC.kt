package org.brightify.hyperdrive.krpc.api.impl

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.takeWhile
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdUpstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.DownstreamRPCEvent
import org.brightify.hyperdrive.krpc.api.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.api.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.api.RPCConnection
import org.brightify.hyperdrive.krpc.api.RPCFrame
import org.brightify.hyperdrive.krpc.api.RPCReference
import org.brightify.hyperdrive.krpc.api.UpstreamRPCEvent
import org.brightify.hyperdrive.krpc.api.error.RPCProtocolViolationError
import org.brightify.hyperdrive.krpc.api.throwable

object ColdUpstreamPendingRPC {
    private val <REQUEST, CLIENT_STREAM, RESPONSE> CallDescriptor.ColdUpstream<REQUEST, CLIENT_STREAM, RESPONSE>.clientStreamEventSerializer: KSerializer<out StreamEvent<out CLIENT_STREAM>>
        get() = StreamEventSerializer(clientStreamSerializer, errorSerializer)

    private val <REQUEST, CLIENT_STREAM, RESPONSE> ColdUpstreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>.clientStreamEventSerializer: KSerializer<out StreamEvent<out CLIENT_STREAM>>
        get() = StreamEventSerializer(clientStreamSerializer, errorSerializer)

    class Server<REQUEST, CLIENT_STREAM, RESPONSE>(
        connection: RPCConnection,
        reference: RPCReference,
        call: CallDescriptor.ColdUpstream<REQUEST, CLIENT_STREAM, RESPONSE>,
        onFinished: () -> Unit,
    ): _PendingRPC.Server<REQUEST, CallDescriptor.ColdUpstream<REQUEST, CLIENT_STREAM, RESPONSE>>(connection, reference, call, onFinished) {
        private companion object {
            val logger = Logger<ColdUpstreamPendingRPC.Server<*, *, *>>()
        }

        private val channel = Channel<CLIENT_STREAM>()

        override suspend fun handle(frame: IncomingRPCFrame<UpstreamRPCEvent>) {
            Do exhaustive when (frame.header.event) {
                is UpstreamRPCEvent.Open -> launch {
                    val data = frame.decoder.decodeSerializableValue(call.requestSerializer)

                    val clientStreamFlow = channel
                        .consumeAsFlow()
                        .onStart {
                            retain()
                            frame.startClientStream()
                        }
                        .onCompletion { exception ->
                            frame.closeClientStream()
                            release()
                        }

                    val response = call.perform(data, clientStreamFlow)
                    frame.respond(response)
                }
                UpstreamRPCEvent.Data -> {
                    val event = frame.decoder.decodeSerializableValue(call.clientStreamEventSerializer)

                    Do exhaustive when (event) {
                        is StreamEvent.Next -> channel.send(event.data)
                        is StreamEvent.Complete -> channel.close()
                        is StreamEvent.Error -> channel.close(event.error.throwable())
                    }
                }
                is UpstreamRPCEvent.StreamOperation -> {
                    throw RPCProtocolViolationError("Upstream call doesn't support receiving stream operations from the client as there is no downstream to control.")
                }
                UpstreamRPCEvent.Warning -> {
                    val error = call.errorSerializer.decodeThrowable(frame.decoder)
                    logger.warning(error) { "Received a warning from the client." }
                }
                UpstreamRPCEvent.Error -> {
                    val error = call.errorSerializer.decodeThrowable(frame.decoder)
                    cancel("Error received from the client.", error)
                }
                UpstreamRPCEvent.Cancel -> {
                    cancel("Cancelled by the client.")
                }
            }
        }

        private suspend fun <PAYLOAD> IncomingRPCFrame<UpstreamRPCEvent>.respond(payload: PAYLOAD) {
            connection.send(OutgoingRPCFrame(
                RPCFrame.Header(header.callReference, DownstreamRPCEvent.Response),
                call.responseSerializer as SerializationStrategy<Any?>,
                payload as Any?,
            ))
        }

        private suspend fun IncomingRPCFrame<UpstreamRPCEvent>.startClientStream() {
            connection.send(OutgoingRPCFrame(
                RPCFrame.Header(header.callReference, DownstreamRPCEvent.StreamOperation.Start),
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
    }

    class Client<REQUEST, CLIENT_STREAM, RESPONSE>(
        connection: RPCConnection,
        reference: RPCReference,
        call: ColdUpstreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>,
        private val clientStream: Flow<CLIENT_STREAM>,
        onFinished: () -> Unit,
    ): _PendingRPC.Client<REQUEST, RESPONSE, ColdUpstreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>>(
        connection,
        reference,
        call,
        onFinished,
    ) {
        private companion object {
            val logger = Logger<ColdUpstreamPendingRPC.Client<*, *, *>>()
        }

        private lateinit var upstreamJob: Job
        private val responseDeferred = CompletableDeferred<RESPONSE>()

        override suspend fun perform(payload: REQUEST): RESPONSE = run {
            open(payload)

            responseDeferred.await()
        }

        override suspend fun handle(frame: IncomingRPCFrame<DownstreamRPCEvent>) {
            Do exhaustive when (frame.header.event) {
                DownstreamRPCEvent.Opened -> {
                    throw RPCProtocolViolationError("Upstream Client doesn't accept `Opened` frame.")
                }
                DownstreamRPCEvent.Data -> {
                    throw RPCProtocolViolationError("Upstream Client doesn't accept `Data` frame.")
                }
                DownstreamRPCEvent.Response -> {
                    val payload = frame.decoder.decodeSerializableValue(call.incomingSerializer)
                    responseDeferred.complete(payload)
                }
                DownstreamRPCEvent.StreamOperation.Start -> upstreamJob = launch {
                    clientStream
                        .catch { exception ->
                            sendStreamEvent(StreamEvent.Error(exception))
                        }
                        .collect {
                            sendStreamEvent(StreamEvent.Next(it))
                        }

                    sendStreamEvent(StreamEvent.Complete())
                }
                DownstreamRPCEvent.StreamOperation.Close -> {
                    if (!this::upstreamJob.isInitialized) {
                        return frame.warnUnexpected("Upstream Client's stream was not started. Cannot close.")
                    }
                    upstreamJob.cancel()
                }
                DownstreamRPCEvent.Warning -> {
                    val error = call.errorSerializer.decodeThrowable(frame.decoder)
                    logger.warning(error) { "Received a warning from the server." }
                }
                DownstreamRPCEvent.Error -> {
                    val error = call.errorSerializer.decodeThrowable(frame.decoder)
                    responseDeferred.completeExceptionally(error)
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