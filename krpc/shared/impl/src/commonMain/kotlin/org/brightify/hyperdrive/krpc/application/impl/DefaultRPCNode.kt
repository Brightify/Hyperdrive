package org.brightify.hyperdrive.krpc.application.impl

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.ServiceRegistry
import org.brightify.hyperdrive.krpc.application.HandshakeFailedException
import org.brightify.hyperdrive.krpc.application.PayloadSerializer
import org.brightify.hyperdrive.krpc.application.RPCNode
import org.brightify.hyperdrive.krpc.application.RPCNodeExtension
import org.brightify.hyperdrive.krpc.impl.MutableConcatServiceRegistry
import org.brightify.hyperdrive.krpc.impl.ProtocolBasedRPCTransport
import org.brightify.hyperdrive.krpc.protocol.DefaultRPCImplementationRegistry
import org.brightify.hyperdrive.krpc.protocol.DefaultRPCInterceptorRegistry
import org.brightify.hyperdrive.krpc.protocol.InterceptorEnabledRPCTransport
import org.brightify.hyperdrive.krpc.protocol.InterceptorEnabledServiceRegistry
import org.brightify.hyperdrive.krpc.extension.RPCExtensionServiceRegistry
import org.brightify.hyperdrive.krpc.application.RPCHandshakePerformer
import org.brightify.hyperdrive.krpc.protocol.RPCProtocol

public class DefaultRPCNode(
    override val contract: Contract,
    public val transport: RPCTransport,
): RPCNode {
    private companion object {
        val logger = Logger<DefaultRPCNode>()
    }

    override fun <E: RPCNodeExtension> getExtension(identifier: RPCNodeExtension.Identifier<E>): E? {
        @Suppress("UNCHECKED_CAST")
        return contract.extensions[identifier] as? E
    }

    public class Contract(
        override val payloadSerializer: PayloadSerializer,
        internal val protocol: RPCProtocol,
        internal val extensions: Map<RPCNodeExtension.Identifier<*>, RPCNodeExtension>,
    ): RPCNode.Contract

    public class Factory(
        private val handshakePerformer: RPCHandshakePerformer,
        private val payloadSerializerFactory: PayloadSerializer.Factory,
        private val extensionFactories: List<RPCNodeExtension.Factory<*>>,
        private val providedServiceRegistry: ServiceRegistry,
    ) {
        public suspend fun create(connection: RPCConnection): DefaultRPCNode {
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

    public suspend fun run(onInitializationCompleted: suspend () -> Unit): Unit = coroutineScope {
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