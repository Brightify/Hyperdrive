package org.brightify.hyperdrive.krpc.protocol

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.krpc.description.ColdBistreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdDownstreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.SingleCallDescription

public class DefaultRPCInterceptorRegistry(
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

    public sealed class ChainingIncomingInterceptor: RPCIncomingInterceptor {
        public class Middle(
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
        public object Tail: ChainingIncomingInterceptor()

        public companion object {
            public operator fun invoke(interceptors: List<RPCIncomingInterceptor>): ChainingIncomingInterceptor {
                return interceptors.foldRight(Tail as ChainingIncomingInterceptor) { interceptor, nextLink ->
                    Middle(interceptor, nextLink)
                }
            }
        }
    }

    public sealed class ChainingOutgoingInterceptor: RPCOutgoingInterceptor {
        public class Middle(
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
        public object Tail: ChainingOutgoingInterceptor()

        public companion object {
            public operator fun invoke(interceptors: List<RPCOutgoingInterceptor>): ChainingOutgoingInterceptor {
                return interceptors.foldRight(Tail as ChainingOutgoingInterceptor) { interceptor, nextLink ->
                    Middle(interceptor, nextLink)
                }
            }
        }
    }
}