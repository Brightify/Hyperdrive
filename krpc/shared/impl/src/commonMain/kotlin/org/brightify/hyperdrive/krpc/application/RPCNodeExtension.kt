package org.brightify.hyperdrive.krpc.application

import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import org.brightify.hyperdrive.krpc.protocol.RPCIncomingInterceptor
import org.brightify.hyperdrive.krpc.protocol.RPCOutgoingInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

public interface RPCNodeExtension: RPCIncomingInterceptor, RPCOutgoingInterceptor {
    public interface Identifier<E: RPCNodeExtension> {
        public val uniqueIdentifier: String
        public val extensionClass: KClass<E>
    }

    public val providedServices: List<ServiceDescription>
        get() = emptyList()

    /**
     * Binds an extension to a transport. The provided transport is already intercepted by all registered extensions including this one.
     * The extension is allowed to store parts of the contract or the whole contract.
     */
    public suspend fun bind(transport: RPCTransport, contract: RPCNode.Contract)

    public suspend fun enhanceParallelWorkContext(context: CoroutineContext): CoroutineContext = context

    public suspend fun whileConnected() { }

    public interface Factory<E: RPCNodeExtension> {
        /**
         * Identifier of the RPC extension. It's required to always be unique.
         */
        public val identifier: Identifier<E>

        /**
         * Whether the extension should only be created when it's available both locally and remotely.
         */
        public val isRequiredOnOtherSide: Boolean

        public fun create(): E
    }
}

