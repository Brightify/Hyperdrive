package org.brightify.hyperdrive.krpc.application

import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import org.brightify.hyperdrive.krpc.protocol.RPCIncomingInterceptor
import org.brightify.hyperdrive.krpc.protocol.RPCOutgoingInterceptor
import org.brightify.hyperdrive.krpc.protocol.ascension.PayloadSerializer
import kotlin.reflect.KClass

interface RPCNode {
    val contract: Contract

    fun <E: RPCNodeExtension> getExtension(identifier: RPCNodeExtension.Identifier<E>): E?

    suspend fun close()

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

