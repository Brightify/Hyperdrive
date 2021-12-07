package org.brightify.hyperdrive.krpc.application.runner

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.api.throwable
import org.brightify.hyperdrive.krpc.application.PayloadSerializer
import org.brightify.hyperdrive.krpc.description.ColdDownstreamCallDescription
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.error.asRPCError
import org.brightify.hyperdrive.krpc.protocol.RPC
import kotlin.coroutines.cancellation.CancellationException

public object ColdDownstreamRunner {
    public class Callee<REQUEST, SERVER_STREAM>(
        private val serializer: PayloadSerializer,
        private val call: RunnableCallDescription.ColdDownstream<REQUEST, SERVER_STREAM>,
    ): RPC.Downstream.Callee.Implementation {
        private val streamEventSerializer = StreamEvent.Serializer(
            call.responseSerializer,
            call.errorSerializer,
        )

        override suspend fun perform(payload: SerializedPayload): RPC.StreamOrError {
            val request = serializer.deserialize(call.requestSerializer, payload)

            return try {
                val stream = call.perform(request)
                flow {
                    try {
                        stream.collect {
                            emit(serializer.serialize(streamEventSerializer, StreamEvent.Element(it)))
                        }

                        emit(serializer.serialize(streamEventSerializer, StreamEvent.Complete()))
                    } catch (t: Throwable) {
                        emit(serializer.serialize(streamEventSerializer, StreamEvent.Error(t)))
                    }
                }.let(RPC.StreamOrError::Stream)
            } catch (t: Throwable) {
                RPC.StreamOrError.Error(serializer.serialize(call.errorSerializer, t.asRPCError()))
            }
        }
    }

    public class Caller<REQUEST, SERVER_STREAM>(
        private val serializer: PayloadSerializer,
        private val rpc: RPC.Downstream.Caller,
        private val call: ColdDownstreamCallDescription<REQUEST, SERVER_STREAM>,
    ) {
        private val streamEventSerializer = StreamEvent.Serializer(
            call.serverStreamSerializer,
            call.errorSerializer,
        )

        public suspend fun run(payload: REQUEST): Flow<SERVER_STREAM> {
            val serializedPayload = serializer.serialize(call.outgoingSerializer, payload)

            return when (val serializedStreamOrError = rpc.perform(serializedPayload)) {
                is RPC.StreamOrError.Stream -> serializedStreamOrError.stream
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
                is RPC.StreamOrError.Error -> {
                    val error = serializer.deserialize(call.errorSerializer, serializedStreamOrError.error)
                    throw error.throwable()
                }
            }
        }
    }
}