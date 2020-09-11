package org.brightify.hyperdrive.krpc.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.reflect.KType

interface ServiceDescriptor<S> {
    fun describe(service: S): ServiceDescription
}

data class ServiceDescription(
    val identifier: String,
    val calls: List<CallDescriptor>
)

data class CallDescriptor(
    val identifier: ServiceCallIdentifier,
    val requestSerializer: KSerializer<out Any?>?,
    val responseSerializer: KSerializer<out Any?>?,
    val perform: suspend (Any?) -> Any?
)

data class ClientCallDescriptor<REQUEST, RESPONSE>(
    val identifier: ServiceCallIdentifier,
    val outgoingSerializer: KSerializer<REQUEST>?,
    val incomingSerializer: KSerializer<RESPONSE>?,
) {
    fun with(completable: CompletableDeferred<RESPONSE>): CallDescriptor {
        return CallDescriptor(
            identifier,
            outgoingSerializer,
            incomingSerializer
        ) {
            // TODO: Check for the type and throw if not correct!
            completable.complete(it as RESPONSE)
        }
    }
}

interface ServiceCall {

}

@Serializable
data class Context(
    val metadata: Map<String, String>
)

