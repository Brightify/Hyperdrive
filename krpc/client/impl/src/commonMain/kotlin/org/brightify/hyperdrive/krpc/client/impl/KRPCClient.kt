package org.brightify.hyperdrive.krpc.client.impl

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.ServiceRegistry
import org.brightify.hyperdrive.krpc.application.PayloadSerializer
import org.brightify.hyperdrive.krpc.application.RPCNodeExtension
import org.brightify.hyperdrive.krpc.application.impl.DefaultRPCHandshakePerformer
import org.brightify.hyperdrive.krpc.application.impl.DefaultRPCNode
import org.brightify.hyperdrive.krpc.client.RPCClientConnector
import org.brightify.hyperdrive.krpc.description.ColdBistreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdDownstreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.error.ConnectionClosedException
import org.brightify.hyperdrive.krpc.extension.SessionNodeExtension
import org.brightify.hyperdrive.krpc.impl.SerializerRegistry
import org.brightify.hyperdrive.krpc.session.Session
import org.brightify.hyperdrive.krpc.session.SessionContextKeyRegistry
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer
import kotlin.coroutines.cancellation.CancellationException

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
        return requireNotNull(activeNode().getExtension(SessionNodeExtension.Identifier)?.session) {
            "Couldn't get session, probably the other party doesn't have the session extension active."
        }
    }

    suspend fun <T> withSession(block: Session.() -> T): T {
        val session = requireSession()
        return session.block()
    }

    private val handshakePerformer = DefaultRPCHandshakePerformer(frameSerializerFactory, DefaultRPCHandshakePerformer.Behavior.Client)
    private val combinedExtensions = builtinExtensions + additionalExtensions
    private val nodeFactory = DefaultRPCNode.Factory(handshakePerformer, payloadSerializerFactory, combinedExtensions, serviceRegistry)

    private val activeNode = MutableStateFlow<ActiveNode?>(null)

    private val callQueue = Channel<suspend RPCTransport.() -> Unit>()

    // FIXME: Do we need `supervisorScope` instead of `coroutineScope`?
    suspend fun run() = coroutineScope {
        while (isActive) {
            try {
                logger.info { "Will create connection." }
                connector.withConnection {
                    val connection = this
                    logger.info { "Connection created: $this" }
                    val node = nodeFactory.create(this)
                    node.run {
                        logger.info { "Client node initialized." }
                        activeNode.value = ActiveNode(node, connection)
                        connection.coroutineContext.job.invokeOnCompletion {
                            activeNode.value = null
                        }
                    }
                    logger.info { "Releasing connection: $this" }
                    activeNode.value = null
                }
                logger.info { "Client connection completed. Trying to reconnect soon." }
                delay(500)
            } catch (t: CancellationException) {
                activeNode.value = null
                if (isActive) {
                    logger.warning(t) { "Client connection cancelled. Client is still active, will reconnect in 500ms." }
                    delay(500)
                } else {
                    throw t
                }
            } catch (t: Throwable) {
                activeNode.value = null
                if (t is ConnectionClosedException || connector.isConnectionCloseException(t)) {
                    logger.warning(t) { "Client connection disconnected. Trying to reconnect in 500ms." }
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

    suspend fun stop() {
        activeNode.value?.connection?.close()
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

    private suspend fun activeNode(): DefaultRPCNode = activeNode.filterNotNull().first {
        it.node.isActive
    }.node

    private data class ActiveNode(val node: DefaultRPCNode, val connection: RPCConnection)
}
