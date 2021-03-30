package org.brightify.hyperdrive.krpc.application

import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.LoggingLevel
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import org.brightify.hyperdrive.krpc.protocol.RPCIncomingInterceptor
import org.brightify.hyperdrive.krpc.protocol.RPCOutgoingInterceptor

interface RPCExtension: RPCIncomingInterceptor, RPCOutgoingInterceptor {
    class Identifier(val value: String)

    val providedServices: List<ServiceDescription>
        get() = emptyList()

    suspend fun bind(transport: RPCTransport)

    interface Factory {
        val identifier: Identifier

        fun create(): RPCExtension
    }
}

class CallLoggingExtension(
    private val logger: Logger = Logger<CallLoggingExtension>(),
    private val level: LoggingLevel,
): RPCExtension {

    override suspend fun bind(transport: RPCTransport) {
        logger.logIfEnabled(level) { "Logging enabled for $transport (Incoming Single Call Only)" }
    }

    private inline fun log(throwable: Throwable? = null, crossinline block: () -> String) {
        logger.logIfEnabled(level, throwable) { block() }
    }

    override suspend fun <PAYLOAD, RESPONSE> interceptIncomingSingleCall(
        payload: PAYLOAD,
        call: RunnableCallDescription.Single<PAYLOAD, RESPONSE>,
        next: suspend (PAYLOAD) -> RESPONSE,
    ): RESPONSE {
        log { "REQUEST: ${call.identifier}($payload)" }
        return try {
            val response = super.interceptIncomingSingleCall(payload, call, next)
            log { "SUCCESS: ${call.identifier}($payload) = $response" }
            response
        } catch (t: Throwable) {
            log(t) { "ERROR: ${call.identifier}($payload) thrown ${t.message}" }
            throw t
        }
    }

}