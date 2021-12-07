package org.brightify.hyperdrive.krpc.server.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.ServiceRegistry
import org.brightify.hyperdrive.krpc.extension.SessionNodeExtension
import org.brightify.hyperdrive.krpc.application.RPCNode
import org.brightify.hyperdrive.krpc.application.RPCNodeExtension
import org.brightify.hyperdrive.krpc.application.impl.DefaultRPCNode
import org.brightify.hyperdrive.krpc.application.impl.DefaultRPCHandshakePerformer
import org.brightify.hyperdrive.krpc.application.PayloadSerializer
import org.brightify.hyperdrive.krpc.server.ServerConnector
import org.brightify.hyperdrive.krpc.session.SessionContextKeyRegistry
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer

public class KRPCServer(
    private val connector: ServerConnector,
    private val runScope: CoroutineScope,
    private val frameSerializerFactory: TransportFrameSerializer.Factory,
    private val payloadSerializerFactory: PayloadSerializer.Factory,
    private val serviceRegistry: ServiceRegistry,
    private val sessionContextKeyRegistry: SessionContextKeyRegistry,
    private val additionalExtensions: List<RPCNodeExtension.Factory<*>> = emptyList(),
): CoroutineScope by runScope + CoroutineName("KRPCServer") + SupervisorJob(runScope.coroutineContext[Job]) {
    private companion object {
        val logger = Logger<KRPCServer>()
    }

    private val handshakePerformer = DefaultRPCHandshakePerformer(frameSerializerFactory, DefaultRPCHandshakePerformer.Behavior.Server)

    private val builtinExtensions = listOf<RPCNodeExtension.Factory<*>>(
        SessionNodeExtension.Factory(sessionContextKeyRegistry, payloadSerializerFactory),
    )

    public val connections: Set<RPCConnection>
        get() = nodeStorage.keys.toSet()

    public val nodes: Set<RPCNode>
        get() = nodeStorage.values.toSet()

    private val nodeStorage = mutableMapOf<RPCConnection, RPCNode>()
    private val nodeStorageLock = Mutex()

    public suspend fun run(): Unit = withContext(coroutineContext) {
        while (isActive) {
            val connection = connector.nextConnection()
            logger.debug { "New connection: $connection" }
            // TODO: Check if we can make this launch die when the `runningJob` is canceled.
            connection.launch {
                logger.debug { "Launched on connection: $connection" }
                try {
                    val node = DefaultRPCNode.Factory(
                        handshakePerformer,
                        payloadSerializerFactory,
                        builtinExtensions + additionalExtensions,
                        serviceRegistry
                    ).create(connection)
                    logger.trace { "Node created" }

                    nodeStorageLock.withLock {
                        nodeStorage[connection] = node
                    }

                    node.run {
                        logger.debug { "Server node initialized." }
                    }

                    logger.debug { "Node stopped." }
                    connection.close()
                } catch (t: Throwable) {
                    logger.warning(t) { "Connection ended." }
                } finally {
                    nodeStorageLock.withLock {
                        nodeStorage.remove(connection)
                    }
                }
            }
        }
    }

    public fun start(): Job = launch {
        run()
    }

    public suspend fun close() {
        coroutineContext.job.cancelAndJoin()
    }
}
