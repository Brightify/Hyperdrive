package org.brightify.hyperdrive.krpc.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

sealed class CallDescriptor(val identifier: ServiceCallIdentifier, val errorSerializer: RPCErrorSerializer) {
    class Single<REQUEST, RESPONSE>(
        identifier: ServiceCallIdentifier,
        val requestSerializer: KSerializer<out REQUEST>,
        val responseSerializer: KSerializer<out RESPONSE>,
        errorSerializer: RPCErrorSerializer,
        val perform: suspend (REQUEST) -> RESPONSE,
    ): CallDescriptor(identifier, errorSerializer)

    class ColdUpstream<REQUEST, CLIENT_STREAM, RESPONSE>(
        identifier: ServiceCallIdentifier,
        val requestSerializer: KSerializer<out REQUEST>,
        val clientStreamSerializer: KSerializer<out CLIENT_STREAM>,
        val responseSerializer: KSerializer<out RESPONSE>,
        errorSerializer: RPCErrorSerializer,
        val perform: suspend (REQUEST, Flow<CLIENT_STREAM>) -> RESPONSE,
    ): CallDescriptor(identifier, errorSerializer)

    class ColdDownstream<REQUEST, SERVER_STREAM>(
        identifier: ServiceCallIdentifier,
        val requestSerializer: KSerializer<out REQUEST>?,
        val responseSerializer: KSerializer<out SERVER_STREAM>?,
        errorSerializer: RPCErrorSerializer,
        val perform: suspend (REQUEST) -> Flow<SERVER_STREAM>,
    ): CallDescriptor(identifier, errorSerializer)

    class ColdBistream<REQUEST, CLIENT_STREAM, SERVER_STREAM>(
        identifier: ServiceCallIdentifier,
        val requestSerializer: KSerializer<out REQUEST>?,
        val clientStreamSerializer: KSerializer<out CLIENT_STREAM>?,
        val responseSerializer: KSerializer<out SERVER_STREAM>?,
        errorSerializer: RPCErrorSerializer,
        val perform: suspend (REQUEST, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>,
    ): CallDescriptor(identifier, errorSerializer)
}