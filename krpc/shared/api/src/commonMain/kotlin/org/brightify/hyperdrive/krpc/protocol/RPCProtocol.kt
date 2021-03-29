package org.brightify.hyperdrive.krpc.protocol

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.ServiceRegistry
import org.brightify.hyperdrive.krpc.description.ColdBistreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdDownstreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer
import kotlin.reflect.KClass

interface RPCImplementationRegistry {
    fun <T: RPC.Implementation> callImplementation(id: ServiceCallIdentifier, type: KClass<T>): T
}

inline fun <reified T: RPC.Implementation> RPCImplementationRegistry.callImplementation(id: ServiceCallIdentifier): T {
    return callImplementation(id, T::class)
}

interface RPC {
    interface Implementation

    interface SingleCall: RPC {
        interface Callee: SingleCall {
            interface Implementation: RPC.Implementation {
                suspend fun perform(payload: SerializedPayload): SerializedPayload
            }
        }

        interface Caller: SingleCall {
            suspend fun perform(payload: SerializedPayload): SerializedPayload
        }
    }

    interface Upstream: RPC {
        interface Callee: Upstream {
            interface Implementation: RPC.Implementation {
                suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): SerializedPayload
            }
        }

        interface Caller: Upstream {
            suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): SerializedPayload
        }
    }

    interface Downstream: RPC {
        interface Callee: Downstream {
            interface Implementation: RPC.Implementation {
                suspend fun perform(payload: SerializedPayload): StreamOrError
            }
        }

        interface Caller: Downstream {
            suspend fun perform(payload: SerializedPayload): StreamOrError
        }
    }

    interface Bistream: RPC {
        interface Callee: Bistream {
            interface Implementation: RPC.Implementation {
                suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): StreamOrError
            }
        }

        interface Caller: Bistream {
            suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): StreamOrError
        }
    }

    sealed class StreamOrError {
        class Stream(val stream: Flow<SerializedPayload>): StreamOrError()
        class Error(val error: SerializedPayload): StreamOrError()
    }
}

interface RPCProtocol: CoroutineScope {
    val version: Version

    suspend fun run()

    suspend fun singleCall(serviceCallIdentifier: ServiceCallIdentifier): RPC.SingleCall.Caller

    suspend fun upstream(serviceCallIdentifier: ServiceCallIdentifier): RPC.Upstream.Caller

    suspend fun downstream(serviceCallIdentifier: ServiceCallIdentifier): RPC.Downstream.Caller

    suspend fun bistream(serviceCallIdentifier: ServiceCallIdentifier): RPC.Bistream.Caller

    suspend fun close()

    @Serializable
    enum class Version(val literal: Int) {
        Ascension(1),
    }

    interface Factory {
        val version: Version

        fun create(
            connection: RPCConnection,
            frameSerializer: TransportFrameSerializer,
            implementationRegistry: RPCImplementationRegistry
        ): RPCProtocol
    }
}

interface RPCInterceptorRegistry {
    fun combinedIncomingInterceptor(): RPCIncomingInterceptor

    fun combinedOutgoingInterceptor(): RPCOutgoingInterceptor
}

interface MutableRPCInterceptorRegistry: RPCInterceptorRegistry {
    fun registerIncomingInterceptor(interceptor: RPCIncomingInterceptor)

    fun registerOutgoingInterceptor(interceptor: RPCOutgoingInterceptor)

    fun registerInterceptor(interceptor: RPCInterceptor)
}

class DefaultRPCInterceptorRegistry(
    initialIncomingInterceptors: List<RPCIncomingInterceptor> = emptyList(),
    initialOutgoingInterceptors: List<RPCOutgoingInterceptor> = emptyList(),
): MutableRPCInterceptorRegistry {
    private val incomingInterceptors = initialIncomingInterceptors.toMutableList()
    private val outgoingInterceptors = initialOutgoingInterceptors.toMutableList()

    private var combinedIncomingInterceptor = ChainingIncomingInterceptor(incomingInterceptors)
    private var combinedOutgoingInterceptor = ChainingOutgoingInterceptor(outgoingInterceptors)

    override fun combinedIncomingInterceptor(): RPCIncomingInterceptor = combinedIncomingInterceptor

    override fun combinedOutgoingInterceptor(): RPCOutgoingInterceptor = combinedOutgoingInterceptor

    override fun registerIncomingInterceptor(interceptor: RPCIncomingInterceptor) {
        incomingInterceptors.add(interceptor)
        updateCombinedIncomingInterceptor()
    }

    override fun registerOutgoingInterceptor(interceptor: RPCOutgoingInterceptor) {
        outgoingInterceptors.add(interceptor)
        updateCombinedOutgoingInterceptor()
    }

    override fun registerInterceptor(interceptor: RPCInterceptor) {
        incomingInterceptors.add(interceptor)
        outgoingInterceptors.add(interceptor)
        updateCombinedIncomingInterceptor()
        updateCombinedOutgoingInterceptor()
    }

    private fun updateCombinedIncomingInterceptor() {
        combinedIncomingInterceptor = ChainingIncomingInterceptor(incomingInterceptors)
    }

    private fun updateCombinedOutgoingInterceptor() {
        combinedOutgoingInterceptor = ChainingOutgoingInterceptor(outgoingInterceptors)
    }

    sealed class ChainingIncomingInterceptor: RPCIncomingInterceptor {
        class Middle(
            private val interceptor: RPCIncomingInterceptor,
            private val nextLink: ChainingIncomingInterceptor,
        ): ChainingIncomingInterceptor() {
            override suspend fun <PAYLOAD, RESPONSE> interceptIncomingSingleCall(
                payload: PAYLOAD,
                call: RunnableCallDescription.Single<PAYLOAD, RESPONSE>,
                next: suspend (PAYLOAD) -> RESPONSE
            ): RESPONSE {
                return interceptor.interceptIncomingSingleCall(payload, call) { interceptedPayload ->
                    nextLink.interceptIncomingSingleCall(interceptedPayload, call, next)
                }
            }

            override suspend fun <PAYLOAD, CLIENT_STREAM, RESPONSE> interceptIncomingUpstreamCall(
                payload: PAYLOAD,
                stream: Flow<CLIENT_STREAM>,
                call: RunnableCallDescription.ColdUpstream<PAYLOAD, CLIENT_STREAM, RESPONSE>,
                next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> RESPONSE,
            ): RESPONSE {
                return interceptor.interceptIncomingUpstreamCall(payload, stream, call) { interceptedPayload, interceptedStream ->
                    nextLink.interceptIncomingUpstreamCall(interceptedPayload, interceptedStream, call, next)
                }
            }

            override suspend fun <PAYLOAD, SERVER_STREAM> interceptIncomingDownstreamCall(
                payload: PAYLOAD,
                call: RunnableCallDescription.ColdDownstream<PAYLOAD, SERVER_STREAM>,
                next: suspend (PAYLOAD) -> Flow<SERVER_STREAM>,
            ): Flow<SERVER_STREAM> {
                return interceptor.interceptIncomingDownstreamCall(payload, call) { interceptedPayload ->
                    nextLink.interceptIncomingDownstreamCall(interceptedPayload, call, next)
                }
            }

            override suspend fun <PAYLOAD, CLIENT_STREAM, SERVER_STREAM> interceptIncomingBistreamCall(
                payload: PAYLOAD,
                stream: Flow<CLIENT_STREAM>,
                call: RunnableCallDescription.ColdBistream<PAYLOAD, CLIENT_STREAM, SERVER_STREAM>,
                next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>,
            ): Flow<SERVER_STREAM> {
                return interceptor.interceptIncomingBistreamCall(payload, stream, call) { interceptedPayload, interceptedStream ->
                    nextLink.interceptIncomingBistreamCall(interceptedPayload, interceptedStream, call, next)
                }
            }
        }
        // We don't need to implement the intercept methods as default implementation always calls just `next`.
        object Tail: ChainingIncomingInterceptor()

        companion object {
            operator fun invoke(interceptors: List<RPCIncomingInterceptor>): ChainingIncomingInterceptor {
                return interceptors.foldRight(Tail as ChainingIncomingInterceptor) { interceptor, nextLink ->
                    Middle(interceptor, nextLink)
                }
            }
        }
    }

    sealed class ChainingOutgoingInterceptor: RPCOutgoingInterceptor {
        class Middle(
            private val interceptor: RPCOutgoingInterceptor,
            private val nextLink: ChainingOutgoingInterceptor
        ): ChainingOutgoingInterceptor() {
            override suspend fun <PAYLOAD, RESPONSE> interceptOutgoingSingleCall(
                payload: PAYLOAD,
                call: SingleCallDescription<PAYLOAD, RESPONSE>,
                next: suspend (PAYLOAD) -> RESPONSE,
            ): RESPONSE {
                return interceptor.interceptOutgoingSingleCall(payload, call) { interceptedPayload ->
                    nextLink.interceptOutgoingSingleCall(interceptedPayload, call, next)
                }
            }

            override suspend fun <PAYLOAD, CLIENT_STREAM, RESPONSE> interceptOutgoingUpstreamCall(
                payload: PAYLOAD,
                stream: Flow<CLIENT_STREAM>,
                call: ColdUpstreamCallDescription<PAYLOAD, CLIENT_STREAM, RESPONSE>,
                next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> RESPONSE,
            ): RESPONSE {
                return interceptor.interceptOutgoingUpstreamCall(payload, stream, call) { interceptedPayload, interceptedStream ->
                    nextLink.interceptOutgoingUpstreamCall(interceptedPayload, interceptedStream, call, next)
                }
            }

            override suspend fun <PAYLOAD, SERVER_STREAM> interceptOutgoingDownstreamCall(
                payload: PAYLOAD,
                call: ColdDownstreamCallDescription<PAYLOAD, SERVER_STREAM>,
                next: suspend (PAYLOAD) -> Flow<SERVER_STREAM>,
            ): Flow<SERVER_STREAM> {
                return interceptor.interceptOutgoingDownstreamCall(payload, call) { interceptedPayload ->
                    nextLink.interceptOutgoingDownstreamCall(interceptedPayload, call, next)
                }
            }

            override suspend fun <PAYLOAD, CLIENT_STREAM, SERVER_STREAM> interceptOutgoingBistreamCall(
                payload: PAYLOAD,
                stream: Flow<CLIENT_STREAM>,
                call: ColdBistreamCallDescription<PAYLOAD, CLIENT_STREAM, SERVER_STREAM>,
                next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>,
            ): Flow<SERVER_STREAM> {
                return interceptor.interceptOutgoingBistreamCall(payload, stream, call) { interceptedPayload, interceptedStream ->
                    nextLink.interceptOutgoingBistreamCall(interceptedPayload, interceptedStream, call, next)
                }
            }
        }
        // We don't need to implement the intercept methods as default implementation always calls just `next`.
        object Tail: ChainingOutgoingInterceptor()

        companion object {
            operator fun invoke(interceptors: List<RPCOutgoingInterceptor>): ChainingOutgoingInterceptor {
                return interceptors.foldRight(Tail as ChainingOutgoingInterceptor) { interceptor, nextLink ->
                    Middle(interceptor, nextLink)
                }
            }
        }
    }
}

interface RPCIncomingInterceptor {
    suspend fun <PAYLOAD, RESPONSE> interceptIncomingSingleCall(
        payload: PAYLOAD,
        call: RunnableCallDescription.Single<PAYLOAD, RESPONSE>,
        next: suspend (PAYLOAD) -> RESPONSE,
    ): RESPONSE {
        return next(payload)
    }

    suspend fun <PAYLOAD, CLIENT_STREAM, RESPONSE> interceptIncomingUpstreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: RunnableCallDescription.ColdUpstream<PAYLOAD, CLIENT_STREAM, RESPONSE>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> RESPONSE,
    ): RESPONSE {
        return next(payload, stream)
    }

    suspend fun <PAYLOAD, SERVER_STREAM> interceptIncomingDownstreamCall(
        payload: PAYLOAD,
        call: RunnableCallDescription.ColdDownstream<PAYLOAD, SERVER_STREAM>,
        next: suspend (PAYLOAD) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> {
        return next(payload)
    }

    suspend fun <PAYLOAD, CLIENT_STREAM, SERVER_STREAM> interceptIncomingBistreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: RunnableCallDescription.ColdBistream<PAYLOAD, CLIENT_STREAM, SERVER_STREAM>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> {
        return next(payload, stream)
    }
}

interface RPCOutgoingInterceptor {
    suspend fun <PAYLOAD, RESPONSE> interceptOutgoingSingleCall(
        payload: PAYLOAD,
        call: SingleCallDescription<PAYLOAD, RESPONSE>,
        next: suspend (PAYLOAD) -> RESPONSE,
    ): RESPONSE {
        return next(payload)
    }

    suspend fun <PAYLOAD, CLIENT_STREAM, RESPONSE> interceptOutgoingUpstreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: ColdUpstreamCallDescription<PAYLOAD, CLIENT_STREAM, RESPONSE>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> RESPONSE,
    ): RESPONSE {
        return next(payload, stream)
    }

    suspend fun <PAYLOAD, SERVER_STREAM> interceptOutgoingDownstreamCall(
        payload: PAYLOAD,
        call: ColdDownstreamCallDescription<PAYLOAD, SERVER_STREAM>,
        next: suspend (PAYLOAD) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> {
        return next(payload)
    }

    suspend fun <PAYLOAD, CLIENT_STREAM, SERVER_STREAM> interceptOutgoingBistreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: ColdBistreamCallDescription<PAYLOAD, CLIENT_STREAM, SERVER_STREAM>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> {
        return next(payload, stream)
    }
}

interface RPCInterceptor: RPCIncomingInterceptor, RPCOutgoingInterceptor