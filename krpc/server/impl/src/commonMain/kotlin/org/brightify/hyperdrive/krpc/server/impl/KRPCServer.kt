package org.brightify.hyperdrive.krpc.server.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.ServiceRegistry
import org.brightify.hyperdrive.krpc.SessionNodeExtension
import org.brightify.hyperdrive.krpc.application.RPCNode
import org.brightify.hyperdrive.krpc.application.RPCNodeExtension
import org.brightify.hyperdrive.krpc.protocol.DefaultRPCNode
import org.brightify.hyperdrive.krpc.protocol.ascension.DefaultRPCHandshakePerformer
import org.brightify.hyperdrive.krpc.protocol.ascension.PayloadSerializer
import org.brightify.hyperdrive.krpc.server.ServerConnector
import org.brightify.hyperdrive.krpc.session.SessionContextKeyRegistry
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer

class KRPCServer(
    private val connector: ServerConnector,
    private val runScope: CoroutineScope,
    private val frameSerializerFactory: TransportFrameSerializer.Factory,
    private val payloadSerializerFactory: PayloadSerializer.Factory,
    private val serviceRegistry: ServiceRegistry,
    private val sessionContextKeyRegistry: SessionContextKeyRegistry,
    private val additionalExtensions: List<RPCNodeExtension.Factory<*>> = emptyList(),
): CoroutineScope by runScope + SupervisorJob(runScope.coroutineContext[Job]) {
    private companion object {
        val logger = Logger<KRPCServer>()
    }

    private val handshakePerformer = DefaultRPCHandshakePerformer(frameSerializerFactory, DefaultRPCHandshakePerformer.Behavior.Server)

    private val builtinExtensions = listOf<RPCNodeExtension.Factory<*>>(
        SessionNodeExtension.Factory(sessionContextKeyRegistry, payloadSerializerFactory),
    )

    val nodes: Set<RPCNode>
        get() = nodeStorage.values.toSet()

    private val nodeStorage = mutableMapOf<RPCConnection, RPCNode>()
    private val nodeStorageLock = Mutex()

    suspend fun run() = withContext(coroutineContext) {
        while (isActive) {
            val connection = connector.nextConnection()
            // TODO: Check if we can make this launch die when the `runningJob` is canceled.
            connection.launch {
                try {
                    val node = DefaultRPCNode.Factory(
                        handshakePerformer,
                        payloadSerializerFactory,
                        builtinExtensions + additionalExtensions,
                        serviceRegistry
                    ).create(connection)

                    nodeStorageLock.withLock {
                        nodeStorage[connection] = node
                    }

                    node.run {
                        logger.debug { "Server node initialized." }
                    }
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

    fun start() = launch {
        run()
    }

    suspend fun close() {
        coroutineContext.job.cancelAndJoin()
    }
}
