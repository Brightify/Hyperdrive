package org.brightify.hyperdrive.krpc.client.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.ServiceRegistry
import org.brightify.hyperdrive.krpc.SessionNodeExtension
import org.brightify.hyperdrive.krpc.application.RPCNodeExtension
import org.brightify.hyperdrive.krpc.client.RPCClientConnector
import org.brightify.hyperdrive.krpc.description.ColdBistreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdDownstreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.impl.SerializerRegistry
import org.brightify.hyperdrive.krpc.protocol.DefaultRPCNode
import org.brightify.hyperdrive.krpc.protocol.ascension.DefaultRPCHandshakePerformer
import org.brightify.hyperdrive.krpc.protocol.ascension.PayloadSerializer
import org.brightify.hyperdrive.krpc.session.Session
import org.brightify.hyperdrive.krpc.session.SessionContextKeyRegistry
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer

// class KrpcClientBuilder(
// )
//
// fun krpcClient(
//     connector: RPCClientConnector,
//     runScope: CoroutineScope,
//     serializerRegistry: SerializerRegistry,
//     configure: KrpcClientBuilder.() -> Unit
// ): KRPCClient {
//
// }

class KRPCClient(
    private val connector: RPCClientConnector,
    private val runScope: CoroutineScope,
    private val frameSerializerFactory: TransportFrameSerializer.Factory,
    private val payloadSerializerFactory: PayloadSerializer.Factory,
    private val serviceRegistry: ServiceRegistry = ServiceRegistry.Empty,
    private val sessionContextKeyRegistry: SessionContextKeyRegistry,
    private val sessionPlugins: List<SessionNodeExtension.Plugin>,
    private val additionalExtensions: List<RPCNodeExtension.Factory<*>>,
): RPCTransport, CoroutineScope by runScope + SupervisorJob(runScope.coroutineContext[Job]) {
    private companion object {
        val logger = Logger<KRPCClient>()
    }

    constructor(
        connector: RPCClientConnector,
        runScope: CoroutineScope,
        serializerRegistry: SerializerRegistry,
        serviceRegistry: ServiceRegistry = ServiceRegistry.Empty,
        sessionContextKeyRegistry: SessionContextKeyRegistry = SessionContextKeyRegistry.Empty,
        sessionPlugins: List<SessionNodeExtension.Plugin> = emptyList(),
        additionalExtensions: List<RPCNodeExtension.Factory<*>> = emptyList(),
    ): this(
        connector,
        runScope,
        serializerRegistry.transportFrameSerializerFactory,
        serializerRegistry.payloadSerializerFactory,
        serviceRegistry,
        sessionContextKeyRegistry,
        sessionPlugins,
        additionalExtensions,
    )

    private val builtinExtensions = listOf<RPCNodeExtension.Factory<*>>(
        SessionNodeExtension.Factory(sessionContextKeyRegistry, payloadSerializerFactory, sessionPlugins)
    )

    suspend fun requireSession(): Session {
        return requireNotNull(activeNode().getExtension(SessionNodeExtension.Identifier)) {
            "Couldn't get session, probably the other party doesn't have the session extension active."
        }
    }

    suspend fun <T> withSession(block: Session.() -> T): T {
        val session = requireSession()
        return session.block()
    }

    private val activeNode = MutableStateFlow<DefaultRPCNode?>(null)
    private val handshakePerformer = DefaultRPCHandshakePerformer(frameSerializerFactory, DefaultRPCHandshakePerformer.Behavior.Client)
    private val combinedExtensions = builtinExtensions + additionalExtensions

    suspend fun run() = withContext(coroutineContext) {
        while (isActive) {
            try {
                connector.withConnection {
                    logger.info { "Connection created: $this" }
                    val node = DefaultRPCNode.Factory(handshakePerformer, payloadSerializerFactory, combinedExtensions, serviceRegistry).create(this)
                    node.run {
                        logger.info { "Client node initialized." }
                        activeNode.value = node
                    }
                    logger.info { "Relasing connection: $this" }
                }
            } catch (t: Throwable) {
                activeNode.value = null
                if (t is ClosedReceiveChannelException || connector.isConnectionCloseException(t)) {
                    logger.warning(t) { "Client connection disconnected. Trying to reconnect soon." }
                    // Disconnected from server, wait and then try again
                    delay(500)
                } else {
                    logger.error(t) { "Unexpected error! Rethrowing." }
                    throw t
                }
            }
        }
    }

    fun start() = launch {
        run()
    }

    override suspend fun close() {
        coroutineContext.job.cancelAndJoin()
    }

    override suspend fun <REQUEST, RESPONSE> singleCall(serviceCall: SingleCallDescription<REQUEST, RESPONSE>, request: REQUEST): RESPONSE =
        activeTransport().singleCall(serviceCall, request)

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> clientStream(serviceCall: ColdUpstreamCallDescription<REQUEST, CLIENT_STREAM, RESPONSE>, request: REQUEST, clientStream: Flow<CLIENT_STREAM>): RESPONSE =
        activeTransport().clientStream(serviceCall, request, clientStream)

    override suspend fun <REQUEST, RESPONSE> serverStream(
        serviceCall: ColdDownstreamCallDescription<REQUEST, RESPONSE>,
        request: REQUEST
    ): Flow<RESPONSE> = activeTransport().serverStream(serviceCall, request)

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> biStream(
        serviceCall: ColdBistreamCallDescription<REQUEST, CLIENT_STREAM, RESPONSE>,
        request: REQUEST,
        clientStream: Flow<CLIENT_STREAM>
    ): Flow<RESPONSE> = activeTransport().biStream(serviceCall, request, clientStream)

    private suspend fun activeTransport(): RPCTransport = activeNode().transport

    private suspend fun activeNode(): DefaultRPCNode = activeNode.filterNotNull().filter { it.isActive }.first()
}
