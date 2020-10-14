package org.brightify.hyperdrive.krpc.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer
import kotlin.reflect.KType

interface ServiceDescriptor<S> {
    fun describe(service: S): ServiceDescription
}

data class ServiceDescription(
    val identifier: String,
    val calls: List<CallDescriptor>
)

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

data class ClientCallDescriptor<REQUEST, RESPONSE>(
    val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>,
    val incomingSerializer: KSerializer<RESPONSE>,
    val errorSerializer: RPCErrorSerializer,
) {
    fun calling(method: suspend (REQUEST) -> RESPONSE): CallDescriptor {
        return CallDescriptor.Single(
            identifier,
            outgoingSerializer,
            incomingSerializer,
            errorSerializer,
            method,
        )
    }
}

data class ColdUpstreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>(
    val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>,
    val clientStreamSerializer: KSerializer<CLIENT_STREAM>,
    val incomingSerializer: KSerializer<RESPONSE>,
    val errorSerializer: RPCErrorSerializer,
) {
    fun calling(method: suspend (REQUEST, Flow<CLIENT_STREAM>) -> RESPONSE): CallDescriptor {
        return CallDescriptor.ColdUpstream(
            identifier,
            outgoingSerializer,
            clientStreamSerializer,
            incomingSerializer,
            errorSerializer,
            method,
        )
    }
}

data class ColdDownstreamCallDescriptor<REQUEST, SERVER_STREAM>(
    val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>,
    val serverStreamSerializer: KSerializer<SERVER_STREAM>,
    val errorSerializer: RPCErrorSerializer,
) {
    fun calling(method: suspend (REQUEST) -> Flow<SERVER_STREAM>): CallDescriptor {
        return CallDescriptor.ColdDownstream(
            identifier,
            outgoingSerializer,
            serverStreamSerializer,
            errorSerializer,
            method,
        )
    }
}

data class ColdBistreamCallDescriptor<REQUEST, CLIENT_STREAM, SERVER_STREAM>(
    val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>,
    val clientStreamSerializer: KSerializer<CLIENT_STREAM>,
    val serverStreamSerializer: KSerializer<SERVER_STREAM>,
    val errorSerializer: RPCErrorSerializer,
) {
    fun calling(method: suspend (REQUEST, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>): CallDescriptor {
        return CallDescriptor.ColdBistream(
            identifier,
            outgoingSerializer,
            clientStreamSerializer,
            serverStreamSerializer,
            errorSerializer,
            method,
        )
    }
}


interface ServiceCall {

}

@Serializable
data class Context(
    val metadata: Map<String, String>
)

