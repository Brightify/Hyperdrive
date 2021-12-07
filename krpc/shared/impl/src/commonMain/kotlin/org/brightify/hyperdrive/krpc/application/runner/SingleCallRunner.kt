package org.brightify.hyperdrive.krpc.application.runner

import kotlinx.coroutines.CancellationException
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.api.throwable
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.protocol.RPC
import org.brightify.hyperdrive.krpc.application.PayloadSerializer

public object SingleCallRunner {
    public class Callee<REQUEST, RESPONSE>(
        public val serializer: PayloadSerializer,
        public val call: RunnableCallDescription.Single<REQUEST, RESPONSE>,
    ): RPC.SingleCall.Callee.Implementation {
        private val responseSerializer = Response.Serializer(
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

    public class Caller<REQUEST, RESPONSE>(
        public val serializer: PayloadSerializer,
        public val rpc: RPC.SingleCall.Caller,
        public val call: SingleCallDescription<REQUEST, RESPONSE>,
    ) {
        private val responseSerializer = Response.Serializer(
            call.incomingSerializer,
            call.errorSerializer,
        )

        public suspend fun run(payload: REQUEST): RESPONSE {
            val serializedPayload = serializer.serialize(call.outgoingSerializer, payload)
            val serializedResponse = rpc.perform(serializedPayload)
            return when (val response = serializer.deserialize(responseSerializer, serializedResponse)) {
                is Response.Success -> response.response
                is Response.Error -> throw response.error.throwable()
            }
        }
    }
}