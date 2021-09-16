package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.api.throwable
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.error.InternalServerError
import org.brightify.hyperdrive.krpc.error.RPCError
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.frame.AscensionRPCFrame
import org.brightify.hyperdrive.krpc.protocol.RPC
import org.brightify.hyperdrive.krpc.util.RPCReference
import org.brightify.hyperdrive.utils.Do

object SingleCallPendingRPC {
    class Callee(
        protocol: AscensionRPCProtocol,
        scope: CoroutineScope,
        reference: RPCReference,
        private val implementation: RPC.SingleCall.Callee.Implementation
    ): PendingRPC.Callee<AscensionRPCFrame.SingleCall.Upstream, AscensionRPCFrame.SingleCall.Downstream>(protocol, scope, reference, logger), RPC.SingleCall.Callee {
        private companion object {
            val logger = Logger<SingleCallPendingRPC.Callee>()
        }

        override suspend fun handle(frame: AscensionRPCFrame.SingleCall.Upstream) {
            Do exhaustive when (frame) {
                is AscensionRPCFrame.SingleCall.Upstream.Open -> try {
                    val response = implementation.perform(frame.payload)
                    send(AscensionRPCFrame.SingleCall.Downstream.Response(response, reference))
                } finally {
                    complete()
                }
            }
        }
    }

    class Caller(
        protocol: AscensionRPCProtocol,
        scope: CoroutineScope,
        private val serviceCallIdentifier: ServiceCallIdentifier,
        reference: RPCReference,
    ): PendingRPC.Caller<AscensionRPCFrame.SingleCall.Downstream, AscensionRPCFrame.SingleCall.Upstream>(protocol, scope, reference, logger), RPC.SingleCall.Caller {
        private companion object {
            val logger = Logger<SingleCallPendingRPC.Caller>()
        }

        private val responseDeferred = CompletableDeferred<SerializedPayload>()

        override suspend fun perform(payload: SerializedPayload): SerializedPayload = try {
            withContext(this.coroutineContext) {
                send(AscensionRPCFrame.SingleCall.Upstream.Open(payload, serviceCallIdentifier, reference))

                responseDeferred.await()
            }
        } finally {
            complete()
        }

        override suspend fun handle(frame: AscensionRPCFrame.SingleCall.Downstream) {
            // TODO: Do exhaustive doesn't compile for some reason here. Investigate.
            /*Do exhaustive*/ when (frame) {
                is AscensionRPCFrame.SingleCall.Downstream.Response -> responseDeferred.complete(frame.payload)
            }
        }
    }
}

interface PayloadSerializer {
    val format: SerializationFormat

    fun <T> serialize(strategy: SerializationStrategy<T>, payload: T): SerializedPayload

    fun <T> deserialize(strategy: DeserializationStrategy<T>, payload: SerializedPayload): T

    interface Factory {
        val supportedSerializationFormats: List<SerializationFormat>

        fun create(format: SerializationFormat): PayloadSerializer

        fun <T> deserialize(strategy: DeserializationStrategy<T>, payload: SerializedPayload): T

        fun <T> serialize(strategy: SerializationStrategy<T>, value: T): SerializedPayload
    }
}

object SingleCallRunner {
    class Callee<REQUEST, RESPONSE>(
        val serializer: PayloadSerializer,
        val call: RunnableCallDescription.Single<REQUEST, RESPONSE>,
    ): RPC.SingleCall.Callee.Implementation {
        private val responseSerializer = ResponseSerializer(
            call.responseSerializer,
            call.errorSerializer,
        )

        override suspend fun perform(payload: SerializedPayload): SerializedPayload {
            val request = serializer.deserialize(call.requestSerializer, payload)

            return try {
                val response = call.perform(request)
                serializer.serialize(responseSerializer, Response.Success(response))
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                serializer.serialize(responseSerializer, Response.Error(t))
            } finally {

            }
        }
    }

    class Caller<REQUEST, RESPONSE>(
        val serializer: PayloadSerializer,
        val rpc: RPC.SingleCall.Caller,
        val call: SingleCallDescription<REQUEST, RESPONSE>,
    ) {
        private val responseSerializer = ResponseSerializer(
            call.incomingSerializer,
            call.errorSerializer,
        )

        suspend fun run(payload: REQUEST): RESPONSE {
            val serializedPayload = serializer.serialize(call.outgoingSerializer, payload)
            val serializedResponse = rpc.perform(serializedPayload)
            return when (val response = serializer.deserialize(responseSerializer, serializedResponse)) {
                is Response.Success -> response.response
                is Response.Error -> throw response.error.throwable()
            }
        }
    }
}