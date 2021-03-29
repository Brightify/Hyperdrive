package org.brightify.hyperdrive.krpc.protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.ServiceRegistry
import org.brightify.hyperdrive.krpc.application.RPCExtension
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.error.RPCNotFoundError
import org.brightify.hyperdrive.krpc.impl.ProtocolBasedRPCTransport
import org.brightify.hyperdrive.krpc.protocol.ascension.ColdBistreamRunner
import org.brightify.hyperdrive.krpc.protocol.ascension.ColdDownstreamRunner
import org.brightify.hyperdrive.krpc.protocol.ascension.ColdUpstreamRunner
import org.brightify.hyperdrive.krpc.protocol.ascension.PayloadSerializer
import org.brightify.hyperdrive.krpc.protocol.ascension.RPCHandshakePerformer
import org.brightify.hyperdrive.krpc.protocol.ascension.SingleCallRunner
import kotlin.reflect.KClass

class HandshakeFailedException(val rpcMesage: String): Exception("Handshake has failed: $rpcMesage")

class KRPCNode(
    serviceRegistry: ServiceRegistry,
    private val handshakePerformer: RPCHandshakePerformer,
    private val payloadSerializerFactory: PayloadSerializer.Factory,
    private val extensionFactories: List<RPCExtension.Factory>,
    private val connection: RPCConnection,
): RPCImplementationRegistry, CoroutineScope by connection {
    private companion object {
        val logger = Logger<KRPCNode>()
    }

    private val providedServiceRegistry: ServiceRegistry = serviceRegistry
    private lateinit var payloadSerializer: PayloadSerializer
    private lateinit var protocol: RPCProtocol
    private lateinit var extendedServiceRegistry: ServiceRegistry
    private lateinit var extendedTransport: RPCTransport

    private val transportDeferred = CompletableDeferred<RPCTransport>()

    init {
        coroutineContext.job.invokeOnCompletion {
            if (!transportDeferred.isCompleted) {
                transportDeferred.completeExceptionally(it ?: CancellationException("Node connection closed before transport was prepared."))
            }
        }
    }

    suspend fun transport(): RPCTransport = transportDeferred.await()

    suspend fun run() = withContext(coroutineContext) {
        when (val handshakeResult = handshakePerformer.performHandshake(connection)) {
            is RPCHandshakePerformer.HandshakeResult.Success -> {
                payloadSerializer = payloadSerializerFactory.create(handshakeResult.selectedFrameSerializer.format)
                protocol = handshakeResult.selectedProtocolFactory.create(
                    connection,
                    handshakeResult.selectedFrameSerializer,
                    this@KRPCNode,
                )

                // TODO: Check which extensions are supported by the other party.
                val transport = ProtocolBasedRPCTransport(protocol, payloadSerializer)
                val extensions = extensionFactories.map { it.create() }
                val interceptorRegistry = DefaultRPCInterceptorRegistry(extensions, extensions)

                extendedServiceRegistry =
                    InterceptorEnabledServiceRegistry(providedServiceRegistry, interceptorRegistry.combinedIncomingInterceptor())
                extendedTransport = InterceptorEnabledRPCTransport(transport, interceptorRegistry.combinedOutgoingInterceptor())
                transportDeferred.complete(extendedTransport)

                // We need the protocol to be running before we bind the extensions.
                val runningProtocol = async { protocol.run() }

                for (extension in extensions) {
                    extension.bind(transport)
                }

                // We want to end when the protocol does and rethrow any exceptions from it.
                runningProtocol.await()
            }
            is RPCHandshakePerformer.HandshakeResult.Failed -> {
                val exception = HandshakeFailedException(handshakeResult.message)
                transportDeferred.completeExceptionally(exception)
                connection.cancel("Handshake failed: ${handshakeResult.message}.")
                throw exception
            }
        }
    }

    suspend fun close() {
        logger.trace { "Will close connection." }
        coroutineContext.job.cancelAndJoin()
        connection.close()
        logger.trace { "Did close connection." }
    }

    override fun <T: RPC.Implementation> callImplementation(id: ServiceCallIdentifier, type: KClass<T>): T {
        val runnableCall = extendedServiceRegistry.getCallById(id, RunnableCallDescription::class)
        return when (runnableCall) {
            is RunnableCallDescription.Single<*, *> -> SingleCallRunner.Callee(payloadSerializer, runnableCall) as T
            is RunnableCallDescription.ColdUpstream<*, *, *> -> ColdUpstreamRunner.Callee(payloadSerializer, runnableCall) as T
            is RunnableCallDescription.ColdDownstream<*, *> -> ColdDownstreamRunner.Callee(payloadSerializer, runnableCall) as T
            is RunnableCallDescription.ColdBistream<*, *, *> -> ColdBistreamRunner.Callee(payloadSerializer, runnableCall) as T
            null -> throw RPCNotFoundError(id)
        }
    }
}

