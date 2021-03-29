package org.brightify.hyperdrive.krpc.server.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
): CoroutineScope by runScope + SupervisorJob(runScope.coroutineContext[Job]) {
    private val handshakePerformer = DefaultRPCHandshakePerformer(frameSerializerFactory, DefaultRPCHandshakePerformer.Behavior.Server)

    suspend fun run() = withContext(coroutineContext) {
        while (isActive) {
            val connection = connector.nextConnection()
            // TODO: Check if we can make this launch die when the `runningJob` is canceled.
            connection.launch {
                KRPCNode(serviceRegistry, handshakePerformer, payloadSerializerFactory, listOf(), connection).run()
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

/**
 * Service registry that searches a call in multiple registries passed into its constructor. The priority is ascending, so the first non-null
 * call returned from a registry will be used.
 *
 * NOTE: No two calls should ever have the same ID. This is currently not being enforced, but will be in a later version.
 * TODO: Enforce no two calls in registries have the same ID.
 */
class MutableConcatServiceRegistry(
    ascendingRegistries: List<ServiceRegistry> = emptyList(),
): ServiceRegistry {
    constructor(vararg ascendingRegistries: ServiceRegistry): this(ascendingRegistries.toList())

    private val ascendingRegistries = ascendingRegistries.toMutableList()

    fun prepend(registry: ServiceRegistry) {
        ascendingRegistries.add(0, registry)
    }

    fun append(registry: ServiceRegistry) {
        ascendingRegistries.add(registry)
    }

    override fun <T: RunnableCallDescription<*>> getCallById(id: ServiceCallIdentifier, type: KClass<T>): T? {
        for (registry in ascendingRegistries) {
            return registry.getCallById(id, type) ?: continue
        }
        return null
    }
}

class RPCConnectionWithSession(val connection: RPCConnection, val session: Session): RPCConnection by connection {
    override val coroutineContext: CoroutineContext = connection.coroutineContext + session
}