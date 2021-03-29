package org.brightify.hyperdrive.krpc.description

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.protocol.RPCIncomingInterceptor

sealed class RunnableCallDescription<PAYLOAD>(
    val identifier: ServiceCallIdentifier,
    val payloadSerializer: KSerializer<PAYLOAD>,
    val errorSerializer: RPCErrorSerializer,
) {
    class Single<REQUEST, RESPONSE>(
        identifier: ServiceCallIdentifier,
        val requestSerializer: KSerializer<REQUEST>,
        val responseSerializer: KSerializer<RESPONSE>,
        errorSerializer: RPCErrorSerializer,
        val perform: suspend (REQUEST) -> RESPONSE,
    ): RunnableCallDescription<REQUEST>(identifier, requestSerializer, errorSerializer) {
        fun interceptedWith(interceptor: RPCIncomingInterceptor) = Single(
            identifier, requestSerializer, responseSerializer, errorSerializer
        ) { payload ->
            interceptor.interceptIncomingSingleCall(payload, this, perform)
        }
    }

    class ColdUpstream<REQUEST, CLIENT_STREAM, RESPONSE>(
        identifier: ServiceCallIdentifier,
        val requestSerializer: KSerializer<REQUEST>,
        val clientStreamSerializer: KSerializer<CLIENT_STREAM>,
        val responseSerializer: KSerializer<RESPONSE>,
        errorSerializer: RPCErrorSerializer,
        val perform: suspend (REQUEST, Flow<CLIENT_STREAM>) -> RESPONSE,
    ): RunnableCallDescription<REQUEST>(identifier, requestSerializer, errorSerializer) {
        fun interceptedWith(interceptor: RPCIncomingInterceptor) = ColdUpstream(
            identifier, requestSerializer, clientStreamSerializer, responseSerializer, errorSerializer
        ) { payload, stream ->
            interceptor.interceptIncomingUpstreamCall(payload, stream, this, perform)
        }
    }

    class ColdDownstream<REQUEST, SERVER_STREAM>(
        identifier: ServiceCallIdentifier,
        val requestSerializer: KSerializer<REQUEST>,
        val responseSerializer: KSerializer<SERVER_STREAM>,
        errorSerializer: RPCErrorSerializer,
        val perform: suspend (REQUEST) -> Flow<SERVER_STREAM>,
    ): RunnableCallDescription<REQUEST>(identifier, requestSerializer, errorSerializer) {
        fun interceptedWith(interceptor: RPCIncomingInterceptor) = ColdDownstream(
            identifier, requestSerializer, responseSerializer, errorSerializer
        ) { payload ->
            interceptor.interceptIncomingDownstreamCall(payload, this, perform)
        }
    }

    class ColdBistream<REQUEST, CLIENT_STREAM, SERVER_STREAM>(
        identifier: ServiceCallIdentifier,
        val requestSerializer: KSerializer<REQUEST>,
        val clientStreamSerializer: KSerializer<CLIENT_STREAM>,
        val responseSerializer: KSerializer<SERVER_STREAM>,
        errorSerializer: RPCErrorSerializer,
        val perform: suspend (REQUEST, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>,
    ): RunnableCallDescription<REQUEST>(identifier, requestSerializer, errorSerializer) {
        fun interceptedWith(interceptor: RPCIncomingInterceptor) = ColdBistream(
            identifier, requestSerializer, clientStreamSerializer, responseSerializer, errorSerializer
        ) { payload, stream ->
            interceptor.interceptIncomingBistreamCall(payload, stream, this, perform)
        }
    }
}