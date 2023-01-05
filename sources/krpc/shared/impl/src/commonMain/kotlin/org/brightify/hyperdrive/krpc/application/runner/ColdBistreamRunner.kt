package org.brightify.hyperdrive.krpc.application.runner

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.api.throwable
import org.brightify.hyperdrive.krpc.application.PayloadSerializer
import org.brightify.hyperdrive.krpc.description.ColdBistreamCallDescription
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.error.asRPCError
import org.brightify.hyperdrive.krpc.protocol.RPC
import kotlin.coroutines.cancellation.CancellationException

public object ColdBistreamRunner {
    public class Callee<REQUEST, CLIENT_STREAM, SERVER_STREAM>(
        private val serializer: PayloadSerializer,
        private val call: RunnableCallDescription.ColdBistream<REQUEST, CLIENT_STREAM, SERVER_STREAM>,
    ): RPC.Bistream.Callee.Implementation {
        private val clientStreamEventSerializer = StreamEvent.Serializer(
            call.clientStreamSerializer,
            call.errorSerializer,
        )
        private val serverStreamEventSerializer = StreamEvent.Serializer(
            call.responseSerializer,
            call.errorSerializer,
        )

        override suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): RPC.StreamOrError {
            val request = serializer.deserialize(call.requestSerializer, payload)

            val clientStream = stream
                .map {
                    return@map when (val event = serializer.deserialize(clientStreamEventSerializer, it)) {
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
                val serverStream = call.perform(request, clientStream)
                flow {
                    try {
                        serverStream.collect {
                            emit(serializer.serialize(serverStreamEventSerializer, StreamEvent.Element(it)))
                        }
                        emit(serializer.serialize(serverStreamEventSerializer, StreamEvent.Complete()))
                    } catch (t: Throwable) {
                        emit(serializer.serialize(serverStreamEventSerializer, StreamEvent.Error(t)))
                    }
                }.let(RPC.StreamOrError::Stream)
            } catch (t: Throwable) {
                RPC.StreamOrError.Error(serializer.serialize(call.errorSerializer, t.asRPCError()))
            }
        }
    }

    public class Caller<REQUEST, CLIENT_STREAM, SERVER_STREAM>(
        private val serializer: PayloadSerializer,
        private val rpc: RPC.Bistream.Caller,
        private val call: ColdBistreamCallDescription<REQUEST, CLIENT_STREAM, SERVER_STREAM>
    ) {
        private val clientStreamEventSerializer = StreamEvent.Serializer(
            call.clientStreamSerializer,
            call.errorSerializer,
        )
        private val serverStreamEventSerializer = StreamEvent.Serializer(
            call.serverStreamSerializer,
            call.errorSerializer,
        )

        public suspend fun run(payload: REQUEST, stream: Flow<CLIENT_STREAM>): Flow<SERVER_STREAM> {
            val serializedPayload = serializer.serialize(call.outgoingSerializer, payload)

            val serializedClientStream = flow {
                try {
                    stream.collect {
                        emit(serializer.serialize(clientStreamEventSerializer, StreamEvent.Element(it)))
                    }

                    emit(serializer.serialize(clientStreamEventSerializer, StreamEvent.Complete()))
                } catch (t: Throwable) {
                    emit(serializer.serialize(clientStreamEventSerializer, StreamEvent.Error(t)))
                }
            }
            return when (val serializedStreamOrError = rpc.perform(serializedPayload, serializedClientStream)) {
                is RPC.StreamOrError.Stream -> serializedStreamOrError.stream
                    .map {
                        return@map when (val event = serializer.deserialize(serverStreamEventSerializer, it)) {
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