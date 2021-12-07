package org.brightify.hyperdrive.krpc.description

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.protocol.RPCIncomingInterceptor

public sealed class RunnableCallDescription<PAYLOAD>(
    public val identifier: ServiceCallIdentifier,
    public val payloadSerializer: KSerializer<PAYLOAD>,
    public val errorSerializer: RPCErrorSerializer,
) {
    public class Single<REQUEST, RESPONSE>(
        identifier: ServiceCallIdentifier,
        public val requestSerializer: KSerializer<REQUEST>,
        public val responseSerializer: KSerializer<RESPONSE>,
        errorSerializer: RPCErrorSerializer,
        public val perform: suspend (REQUEST) -> RESPONSE,
    ): RunnableCallDescription<REQUEST>(identifier, requestSerializer, errorSerializer) {
        public fun interceptedWith(interceptor: RPCIncomingInterceptor): Single<REQUEST, RESPONSE> = Single(
            identifier, requestSerializer, responseSerializer, errorSerializer
        ) { payload ->
            interceptor.interceptIncomingSingleCall(payload, this, perform)
        }
    }

    public class ColdUpstream<REQUEST, CLIENT_STREAM, RESPONSE>(
        identifier: ServiceCallIdentifier,
        public val requestSerializer: KSerializer<REQUEST>,
        public val clientStreamSerializer: KSerializer<CLIENT_STREAM>,
        public val responseSerializer: KSerializer<RESPONSE>,
        errorSerializer: RPCErrorSerializer,
        public val perform: suspend (REQUEST, Flow<CLIENT_STREAM>) -> RESPONSE,
    ): RunnableCallDescription<REQUEST>(identifier, requestSerializer, errorSerializer) {
        public fun interceptedWith(interceptor: RPCIncomingInterceptor): ColdUpstream<REQUEST, CLIENT_STREAM, RESPONSE> = ColdUpstream(
            identifier, requestSerializer, clientStreamSerializer, responseSerializer, errorSerializer
        ) { payload, stream ->
            interceptor.interceptIncomingUpstreamCall(payload, stream, this, perform)
        }
    }

    public class ColdDownstream<REQUEST, SERVER_STREAM>(
        identifier: ServiceCallIdentifier,
        public val requestSerializer: KSerializer<REQUEST>,
        public val responseSerializer: KSerializer<SERVER_STREAM>,
        errorSerializer: RPCErrorSerializer,
        public val perform: suspend (REQUEST) -> Flow<SERVER_STREAM>,
    ): RunnableCallDescription<REQUEST>(identifier, requestSerializer, errorSerializer) {
        public fun interceptedWith(interceptor: RPCIncomingInterceptor): ColdDownstream<REQUEST, SERVER_STREAM> = ColdDownstream(
            identifier, requestSerializer, responseSerializer, errorSerializer
        ) { payload ->
            interceptor.interceptIncomingDownstreamCall(payload, this, perform)
        }
    }

    public class ColdBistream<REQUEST, CLIENT_STREAM, SERVER_STREAM>(
        identifier: ServiceCallIdentifier,
        public val requestSerializer: KSerializer<REQUEST>,
        public val clientStreamSerializer: KSerializer<CLIENT_STREAM>,
        public val responseSerializer: KSerializer<SERVER_STREAM>,
        errorSerializer: RPCErrorSerializer,
        public val perform: suspend (REQUEST, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>,
    ): RunnableCallDescription<REQUEST>(identifier, requestSerializer, errorSerializer) {
        public fun interceptedWith(interceptor: RPCIncomingInterceptor): ColdBistream<REQUEST, CLIENT_STREAM, SERVER_STREAM> = ColdBistream(
            identifier, requestSerializer, clientStreamSerializer, responseSerializer, errorSerializer
        ) { payload, stream ->
            interceptor.interceptIncomingBistreamCall(payload, stream, this, perform)
        }
    }
}