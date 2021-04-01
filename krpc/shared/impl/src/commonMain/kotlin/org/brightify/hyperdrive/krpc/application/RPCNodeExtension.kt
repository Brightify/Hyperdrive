package org.brightify.hyperdrive.krpc.application

import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.LoggingLevel
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import org.brightify.hyperdrive.krpc.protocol.RPCIncomingInterceptor
import org.brightify.hyperdrive.krpc.protocol.RPCOutgoingInterceptor
import org.brightify.hyperdrive.krpc.protocol.ascension.PayloadSerializer
import kotlin.reflect.KClass

interface RPCNode {
    val contract: Contract

    /**
     * Configuration of this node as agreed upon with the node on the other side.
     */
    interface Contract {
        val payloadSerializer: PayloadSerializer
    }
}

interface RPCNodeExtension: RPCIncomingInterceptor, RPCOutgoingInterceptor {
    interface Identifier<E: RPCNodeExtension> {
        val uniqueIdentifier: String
        val extensionClass: KClass<E>
    }

    val providedServices: List<ServiceDescription>
        get() = emptyList()

    /**
     * Binds an extension to a transport. The provided transport is already intercepted by all registered extensions including this one.
     * The extension is allowed to store parts of the contract or the whole contract.
     */
    suspend fun bind(transport: RPCTransport, contract: RPCNode.Contract)

    interface Factory<E: RPCNodeExtension> {
        /**
         * Identifier of the RPC extension. It's required to always be unique.
         */
        val identifier: Identifier<E>

        /**
         * Whether the extension should only be created when it's available both locally and remotely.
         */
        val isRequiredOnOtherSide: Boolean

        fun create(): E
    }
}

class CallLoggingNodeExtension(
    private val logger: Logger,
    private val level: LoggingLevel,
): RPCNodeExtension {

    override suspend fun bind(transport: RPCTransport, contract: RPCNode.Contract) {
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

    object Identifier: RPCNodeExtension.Identifier<CallLoggingNodeExtension> {
        override val uniqueIdentifier = "builtin:CallLogging"
        override val extensionClass = CallLoggingNodeExtension::class
    }

    class Factory(
        private val logger: Logger = Logger<CallLoggingNodeExtension>(),
        private val level: LoggingLevel = LoggingLevel.Debug,
    ): RPCNodeExtension.Factory<CallLoggingNodeExtension> {
        override val identifier = Identifier
        override val isRequiredOnOtherSide = false

        override fun create(): CallLoggingNodeExtension {
            return CallLoggingNodeExtension(logger, level)
        }
    }
}