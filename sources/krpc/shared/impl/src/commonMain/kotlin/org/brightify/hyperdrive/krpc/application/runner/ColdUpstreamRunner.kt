package org.brightify.hyperdrive.krpc.application.runner

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.api.throwable
import org.brightify.hyperdrive.krpc.application.PayloadSerializer
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.protocol.RPC
import kotlin.coroutines.cancellation.CancellationException

public object ColdUpstreamRunner {
    public class Callee<REQUEST, CLIENT_STREAM, RESPONSE>(
        public val serializer: PayloadSerializer,
        public val call: RunnableCallDescription.ColdUpstream<REQUEST, CLIENT_STREAM, RESPONSE>,
    ): RPC.Upstream.Callee.Implementation {
        private val streamEventSerializer = StreamEvent.Serializer(
            call.clientStreamSerializer,
            call.errorSerializer,
        )
        private val responseSerializer = Response.Serializer(
            call.responseSerializer,
            call.errorSerializer,
        )

        override suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): SerializedPayload {
            val request = serializer.deserialize(call.requestSerializer, payload)

            val clientStream = stream
                .map {
                    return@map when (val event = serializer.deserialize(streamEventSerializer, it)) {
                        is StreamEvent.Element -> event.element
                        is StreamEvent.Complete -> throw CancellationException("Stream Completed")
                        is StreamEvent.Error -> throw event.error.throwable()
                    }
                }
                .catch { throwable ->
                    if (throwable !is CancellationException) {
                        throw throwable
                    }
                }

            return try {
                val response = call.perform(request, clientStream)
                serializer.serialize(responseSerializer, Response.Success(response))
            } catch (t: Throwable) {
                serializer.serialize(responseSerializer, Response.Error(t))
            }
        }
    }

    public class Caller<REQUEST, CLIENT_STREAM, RESPONSE>(
        public val serializer: PayloadSerializer,
        public val rpc: RPC.Upstream.Caller,
        public val call: ColdUpstreamCallDescription<REQUEST, CLIENT_STREAM, RESPONSE>,
    ) {
        private val streamEventSerializer = StreamEvent.Serializer(
            call.clientStreamSerializer,
            call.errorSerializer,
        )
        private val responseSerializer = Response.Serializer(
            call.incomingSerializer,
            call.errorSerializer,
        )

        public suspend fun run(payload: REQUEST, stream: Flow<CLIENT_STREAM>): RESPONSE {
            val serializedPayload = serializer.serialize(call.outgoingSerializer, payload)
            val serializedFlow = flow {
                try {
                    stream.collect {
                        emit(serializer.serialize(streamEventSerializer, StreamEvent.Element(it)))
                    }

                    emit(serializer.serialize(streamEventSerializer, StreamEvent.Complete()))
                } catch (t: Throwable) {
                    emit(serializer.serialize(streamEventSerializer, StreamEvent.Error(t)))
                }
            }
            val serializedResponse = rpc.perform(serializedPayload, serializedFlow)
            return when (val response = serializer.deserialize(responseSerializer, serializedResponse)) {
                is Response.Success -> response.response
                is Response.Error -> throw response.error.throwable()
            }
        }
    }
}