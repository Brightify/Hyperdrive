package org.brightify.hyperdrive.krpc.api.impl

import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.SerializationStrategy
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.ClientCallDescriptor
import org.brightify.hyperdrive.krpc.api.DownstreamRPCEvent
import org.brightify.hyperdrive.krpc.api.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.api.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.api.RPCConnection
import org.brightify.hyperdrive.krpc.api.RPCFrame
import org.brightify.hyperdrive.krpc.api.RPCReference
import org.brightify.hyperdrive.krpc.api.UpstreamRPCEvent
import org.brightify.hyperdrive.krpc.api.error.RPCProtocolViolationError
import org.brightify.hyperdrive.krpc.api.error.UnknownRPCReferenceException

object SingleCallPendingRPC {
    class Server<REQUEST, RESPONSE>(
        connection: RPCConnection,
        reference: RPCReference,
        call: CallDescriptor.Single<REQUEST, RESPONSE>,
        onFinished: () -> Unit,
    ): _PendingRPC.Server<REQUEST, CallDescriptor.Single<REQUEST, RESPONSE>>(connection, reference, call, onFinished) {
        private companion object {
            val logger = Logger<SingleCallPendingRPC.Server<*, *>>()
        }

        override suspend fun handle(frame: IncomingRPCFrame<UpstreamRPCEvent>) {
            Do exhaustive when (frame.header.event) {
                is UpstreamRPCEvent.Open -> launch {
                    val data = frame.decoder.decodeSerializableValue(call.requestSerializer)
                    frame.respond(
                        call.perform(data)
                    )
                }
                UpstreamRPCEvent.Error -> {
                    val error = frame.decoder.decodeSerializableValue(call.errorSerializer)
                    if (error is UnknownRPCReferenceException) {
                        cancel("Client sent an UnknownRPCReferenceException which probably means we sent it a frame by mistake.", error)
                    } else {
                        throw RPCProtocolViolationError("SingleCall doesn't accept Error frame.")
                    }
                }
                UpstreamRPCEvent.Warning -> {
                    val error = frame.decoder.decodeSerializableValue(call.errorSerializer)
                    logger.warning(error) { "Client sent a warning." }
                }
                UpstreamRPCEvent.Cancel -> {
                    cancel("Client asked to cancel the call.")
                }
                is UpstreamRPCEvent.StreamOperation, UpstreamRPCEvent.Data -> {
                    throw RPCProtocolViolationError("")
                }
            }
        }

        private suspend fun IncomingRPCFrame<UpstreamRPCEvent>.respond(payload: RESPONSE) {
            connection.send(OutgoingRPCFrame(
                RPCFrame.Header(header.callReference, DownstreamRPCEvent.Response),
                call.responseSerializer as SerializationStrategy<Any?>,
                payload as Any?,
            ))
        }
    }

    class Client<REQUEST, RESPONSE>(
        connection: RPCConnection,
        call: ClientCallDescriptor<REQUEST, RESPONSE>,
        reference: RPCReference,
        onFinished: () -> Unit,
    ): _PendingRPC.Client<REQUEST, RESPONSE, ClientCallDescriptor<REQUEST, RESPONSE>>(connection, reference, call, onFinished) {
        private companion object {
            val logger = Logger<SingleCallPendingRPC.Client<*, *>>()
        }

        private val responseDeferred = CompletableDeferred<RESPONSE>()

        override suspend fun perform(payload: REQUEST): RESPONSE = run {
            open(payload)

            val x = try {
                responseDeferred.await()
            } catch (t: Throwable) {
                error("wtf")
            }
            println("ok?")
            x
        }

        override suspend fun handle(frame: IncomingRPCFrame<DownstreamRPCEvent>) {
            Do exhaustive when (frame.header.event) {
                DownstreamRPCEvent.Response -> {
                    responseDeferred.complete(frame.response)
                }
                DownstreamRPCEvent.Warning -> {
                    val error = frame.decoder.decodeSerializableValue(call.errorSerializer)
                    logger.warning { "Received warning from the server. Error: $error" }
                }
                DownstreamRPCEvent.Error -> {
                    val error = frame.decoder.decodeSerializableValue(call.errorSerializer)
                    responseDeferred.completeExceptionally(error)
                }
                DownstreamRPCEvent.Data, DownstreamRPCEvent.Opened, is DownstreamRPCEvent.StreamOperation -> {
                    throw RPCProtocolViolationError("SingleCall only accepts Response frame.")
                }
            }
        }

        private val IncomingRPCFrame<DownstreamRPCEvent>.response: RESPONSE
            get() = decoder.decodeSerializableValue(call.incomingSerializer)
    }
}