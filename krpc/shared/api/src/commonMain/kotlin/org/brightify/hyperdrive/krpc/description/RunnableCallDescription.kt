package org.brightify.hyperdrive.krpc.description

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer

sealed class RunnableCallDescription<PAYLOAD>(
    val identifier: ServiceCallIdentifier,
    val payloadSerializer: KSerializer<PAYLOAD>,
    val errorSerializer: RPCErrorSerializer
) {
    class Single<REQUEST, RESPONSE>(
        identifier: ServiceCallIdentifier,
        val requestSerializer: KSerializer<REQUEST>,
        val responseSerializer: KSerializer<out RESPONSE>,
        errorSerializer: RPCErrorSerializer,
        val perform: suspend (REQUEST) -> RESPONSE,
    ): RunnableCallDescription<REQUEST>(identifier, requestSerializer, errorSerializer)

    class ColdUpstream<REQUEST, CLIENT_STREAM, RESPONSE>(
        identifier: ServiceCallIdentifier,
        val requestSerializer: KSerializer<REQUEST>,
        val clientStreamSerializer: KSerializer<out CLIENT_STREAM>,
        val responseSerializer: KSerializer<out RESPONSE>,
        errorSerializer: RPCErrorSerializer,
        val perform: suspend (REQUEST, Flow<CLIENT_STREAM>) -> RESPONSE,
    ): RunnableCallDescription<REQUEST>(identifier, requestSerializer, errorSerializer)

    class ColdDownstream<REQUEST, SERVER_STREAM>(
        identifier: ServiceCallIdentifier,
        val requestSerializer: KSerializer<REQUEST>,
        val responseSerializer: KSerializer<out SERVER_STREAM>,
        errorSerializer: RPCErrorSerializer,
        val perform: suspend (REQUEST) -> Flow<SERVER_STREAM>,
    ): RunnableCallDescription<REQUEST>(identifier, requestSerializer, errorSerializer)

    class ColdBistream<REQUEST, CLIENT_STREAM, SERVER_STREAM>(
        identifier: ServiceCallIdentifier,
        val requestSerializer: KSerializer<REQUEST>,
        val clientStreamSerializer: KSerializer<out CLIENT_STREAM>,
        val responseSerializer: KSerializer<out SERVER_STREAM>,
        errorSerializer: RPCErrorSerializer,
        val perform: suspend (REQUEST, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>,
    ): RunnableCallDescription<REQUEST>(identifier, requestSerializer, errorSerializer)
}