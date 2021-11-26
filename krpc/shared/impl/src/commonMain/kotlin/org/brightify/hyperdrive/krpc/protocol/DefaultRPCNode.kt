package org.brightify.hyperdrive.krpc.protocol

import kotlinx.coroutines.*
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.ServiceRegistry
import org.brightify.hyperdrive.krpc.api.throwable
import org.brightify.hyperdrive.krpc.application.RPCNode
import org.brightify.hyperdrive.krpc.application.RPCNodeExtension
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.error.RPCNotFoundError
import org.brightify.hyperdrive.krpc.impl.DefaultServiceRegistry
import org.brightify.hyperdrive.krpc.impl.MutableConcatServiceRegistry
import org.brightify.hyperdrive.krpc.impl.ProtocolBasedRPCTransport
import org.brightify.hyperdrive.krpc.protocol.ascension.ColdBistreamRunner
import org.brightify.hyperdrive.krpc.protocol.ascension.ColdDownstreamRunner
import org.brightify.hyperdrive.krpc.protocol.ascension.ColdUpstreamRunner
import org.brightify.hyperdrive.krpc.protocol.ascension.PayloadSerializer
import org.brightify.hyperdrive.krpc.protocol.ascension.RPCHandshakePerformer
import org.brightify.hyperdrive.krpc.protocol.ascension.SingleCallRunner
import kotlin.reflect.KClass

class HandshakeFailedException(val rpcMesage: String): Exception("Handshake has failed: $rpcMesage")

class RPCExtensionServiceRegistry(extensions: List<RPCNodeExtension>): ServiceRegistry {
    private val registry = DefaultServiceRegistry()

    init {
        for (extension in extensions) {
            for (service in extension.providedServices) {
                registry.register(service)
            }
        }
    }

    override fun <T: RunnableCallDescription<*>> getCallById(id: ServiceCallIdentifier, type: KClass<T>): T? {
        return registry.getCallById(id, type)
    }
}

class DefaultRPCNode(
    override val contract: Contract,
    val transport: RPCTransport,
): RPCNode {
    private companion object {
        val logger = Logger<DefaultRPCNode>()
    }

    override fun <E: RPCNodeExtension> getExtension(identifier: RPCNodeExtension.Identifier<E>): E? {
        @Suppress("UNCHECKED_CAST")
        return contract.extensions[identifier] as? E
    }

    class Contract(
        override val payloadSerializer: PayloadSerializer,
        internal val protocol: RPCProtocol,
        internal val extensions: Map<RPCNodeExtension.Identifier<*>, RPCNodeExtension>,
    ): RPCNode.Contract

    class Factory(
        private val handshakePerformer: RPCHandshakePerformer,
        private val payloadSerializerFactory: PayloadSerializer.Factory,
        private val extensionFactories: List<RPCNodeExtension.Factory<*>>,
        private val providedServiceRegistry: ServiceRegistry,
    ) {
        suspend fun create(connection: RPCConnection): DefaultRPCNode {
            when (val handshakeResult = handshakePerformer.performHandshake(connection)) {
                is RPCHandshakePerformer.HandshakeResult.Success -> {
                    val payloadSerializer = payloadSerializerFactory.create(handshakeResult.selectedFrameSerializer.format)

                    // TODO: Check which extensions are supported by the other party first.
                    val extensions = extensionFactories.associate { it.identifier to it.create() }
                    val extensionList = extensions.values.toList()
                    val interceptorRegistry = DefaultRPCInterceptorRegistry(extensionList, extensionList)
                    val extendedServiceRegistry = MutableConcatServiceRegistry(
                        RPCExtensionServiceRegistry(extensionList),
                        InterceptorEnabledServiceRegistry(providedServiceRegistry, interceptorRegistry.combinedIncomingInterceptor())
                    )
                    val implementationRegistry = DefaultRPCImplementationRegistry(payloadSerializer, extendedServiceRegistry)

                    val protocol = handshakeResult.selectedProtocolFactory.create(
                        connection,
                        handshakeResult.selectedFrameSerializer,
                        implementationRegistry,
                    )

                    val transport = ProtocolBasedRPCTransport(protocol, payloadSerializer)

                    val extendedTransport = InterceptorEnabledRPCTransport(transport, interceptorRegistry.combinedOutgoingInterceptor())
                    val contract = Contract(payloadSerializer, protocol, extensions)

                    return DefaultRPCNode(contract, extendedTransport)
                }
                is RPCHandshakePerformer.HandshakeResult.Failed -> {
                    val exception = HandshakeFailedException(handshakeResult.message)
                    connection.cancel("Handshake failed: ${handshakeResult.message}.")
                    throw exception
                }
            }
        }
    }

    suspend fun run(onInitializationCompleted: suspend () -> Unit): Unit = coroutineScope {
        // We need the protocol to be running before we bind the extensions.
        val runningProtocol = async { contract.protocol.run() }

        val extensions = contract.extensions.values
        for (extension in extensions) {
            extension.bind(transport, contract)
        }

        onInitializationCompleted()

        val parallelWorkContext = extensions.fold(coroutineContext) { accumulator, extension ->
            extension.enhanceParallelWorkContext(accumulator)
        }

        val runningParallelWork = launch(parallelWorkContext) {
            extensions.map { extension ->
                async { extension.whileConnected() }
            }.awaitAll()
        }

        // We want to the background work to end when the protocol does.
        runningProtocol.invokeOnCompletion {
            runningParallelWork.cancel("Protocol has completed.", it)
        }
    }
}

class DefaultRPCImplementationRegistry(
    private val payloadSerializer: PayloadSerializer,
    private val serviceRegistry: ServiceRegistry,
): RPCImplementationRegistry {
    override fun <T: RPC.Implementation> callImplementation(id: ServiceCallIdentifier, type: KClass<T>): T {
        val runnableCall = serviceRegistry.getCallById(id, RunnableCallDescription::class)
        @Suppress("UNCHECKED_CAST")
        return when (runnableCall) {
            is RunnableCallDescription.Single<*, *> -> SingleCallRunner.Callee(payloadSerializer, runnableCall) as T
            is RunnableCallDescription.ColdUpstream<*, *, *> -> ColdUpstreamRunner.Callee(payloadSerializer, runnableCall) as T
            is RunnableCallDescription.ColdDownstream<*, *> -> ColdDownstreamRunner.Callee(payloadSerializer, runnableCall) as T
            is RunnableCallDescription.ColdBistream<*, *, *> -> ColdBistreamRunner.Callee(payloadSerializer, runnableCall) as T
            null -> throw RPCNotFoundError(id)
        }
    }
}
