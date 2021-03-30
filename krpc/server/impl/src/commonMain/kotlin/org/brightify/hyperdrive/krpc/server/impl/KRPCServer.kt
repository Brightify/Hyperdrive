package org.brightify.hyperdrive.krpc.server.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.ServiceRegistry
import org.brightify.hyperdrive.krpc.application.RPCExtension
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import org.brightify.hyperdrive.krpc.protocol.KRPCNode
import org.brightify.hyperdrive.krpc.protocol.ascension.DefaultRPCHandshakePerformer
import org.brightify.hyperdrive.krpc.protocol.ascension.PayloadSerializer
import org.brightify.hyperdrive.krpc.server.ServerConnector
import org.brightify.hyperdrive.krpc.session.Session
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

class KRPCServer(
    private val connector: ServerConnector,
    private val runScope: CoroutineScope,
    private val frameSerializerFactory: TransportFrameSerializer.Factory,
    private val payloadSerializerFactory: PayloadSerializer.Factory,
    private val serviceRegistry: ServiceRegistry,
    private val additionalExtensions: List<RPCExtension.Factory> = emptyList(),
): CoroutineScope by runScope + SupervisorJob(runScope.coroutineContext[Job]) {
    private val handshakePerformer = DefaultRPCHandshakePerformer(frameSerializerFactory, DefaultRPCHandshakePerformer.Behavior.Server)

    suspend fun run() = withContext(coroutineContext) {
        while (isActive) {
            val connection = connector.nextConnection()
            // TODO: Check if we can make this launch die when the `runningJob` is canceled.
            connection.launch {
                KRPCNode(serviceRegistry, handshakePerformer, payloadSerializerFactory, additionalExtensions, connection).run()
                connection.close()
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

/**
 * Service registry for builtin kRPC services.
  */
class InternalServiceRegistry(
    contextUpdateService: ServiceDescription,
): ServiceRegistry {
    private val services: Map<ServiceCallIdentifier, RunnableCallDescription<*>> = listOf(
        contextUpdateService
    ).flatMap { it.calls.map { it.identifier to it } }.toMap()

    override fun <T: RunnableCallDescription<*>> getCallById(id: ServiceCallIdentifier, type: KClass<T>): T? {
        return services[id]?.let { it as T }
    }
}

class RPCConnectionWithSession(val connection: RPCConnection, val session: Session): RPCConnection by connection {
    override val coroutineContext: CoroutineContext = connection.coroutineContext + session
}