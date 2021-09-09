package org.brightify.hyperdrive.krpc

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.application.RPCNode
import org.brightify.hyperdrive.krpc.application.RPCNodeExtension
import org.brightify.hyperdrive.krpc.description.CallDescription
import org.brightify.hyperdrive.krpc.description.ColdBistreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdDownstreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import org.brightify.hyperdrive.krpc.description.ServiceDescriptor
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.protocol.ascension.PayloadSerializer
import org.brightify.hyperdrive.krpc.session.SessionContextKeyRegistry
import org.brightify.hyperdrive.krpc.session.Session
import org.brightify.hyperdrive.utils.Do
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass

class SessionNodeExtension(
    private val sessionContextKeyRegistry: SessionContextKeyRegistry,
    private val payloadSerializerFactory: PayloadSerializer.Factory,
    private val plugins: List<Plugin>,
): Session, ContextUpdateService, RPCNodeExtension {
    companion object {
        val logger = Logger<SessionNodeExtension>()
        const val maximumRejections = 10
    }

    object Identifier: RPCNodeExtension.Identifier<SessionNodeExtension> {
        override val uniqueIdentifier: String = "builtin:Session"
        override val extensionClass = SessionNodeExtension::class
    }

    interface Plugin {
        suspend fun onBindComplete(session: Session) { }

        suspend fun onContextChanged(session: Session, modifiedKeys: Set<Session.Context.Key<*>>) { }
    }

    class Factory(
        private val sessionContextKeyRegistry: SessionContextKeyRegistry,
        private val payloadSerializerFactory: PayloadSerializer.Factory,
        private val plugins: List<Plugin> = emptyList(),
    ): RPCNodeExtension.Factory<SessionNodeExtension> {
        override val identifier = Identifier

        override val isRequiredOnOtherSide = true

        override fun create(): SessionNodeExtension {
            return SessionNodeExtension(sessionContextKeyRegistry, payloadSerializerFactory, plugins)
        }
    }

    override val key: CoroutineContext.Key<*> get() = Session

    override val providedServices: List<ServiceDescription> = listOf(
        ContextUpdateService.Descriptor.describe(this)
    )

    private val context = Session.Context(mutableMapOf())
    private val contextModificationLock = Mutex()
    private var runningContextUpdate: Job? = null

    private lateinit var payloadSerializer: PayloadSerializer
    private lateinit var client: ContextUpdateService.Client

    override fun copyOfContext(): Session.Context = context.copy()

    override fun iterator(): Iterator<Session.Context.Item<*>> = context.iterator()

    override suspend fun bind(transport: RPCTransport, contract: RPCNode.Contract) {
        payloadSerializer = contract.payloadSerializer
        client = ContextUpdateService.Client(transport)

        for (plugin in plugins) {
            plugin.onBindComplete(this)
        }
    }

    override suspend fun update(request: ContextUpdateRequest): ContextUpdateResult = contextModificationLock.withLock {
        logger.debug { "Received a session context update request: $request." }
        val modificationsWithKeys = request.modifications.mapKeys { getKeyOrUnsupported(it.key) }
        val rejectedItems = modificationsWithKeys.filter { (key, modification) ->
            modification.oldRevisionOrNull != context[key]?.revision
        }

        if (rejectedItems.isEmpty()) {
            logger.debug { "No reason for a rejection found. Accepting." }

            val modifiedKeys = mutableSetOf<Session.Context.Key<*>>()
            for ((key, modification) in modificationsWithKeys) {
                when (modification) {
                    // No action is needed.
                    is ContextUpdateRequest.Modification.Required -> continue
                    is ContextUpdateRequest.Modification.Set -> {
                        deserializeAndPut(key, modification.newItem)
                        modifiedKeys.add(key)
                    }
                    is ContextUpdateRequest.Modification.Remove -> {
                        context.remove(key)
                        modifiedKeys.add(key)
                    }
                }
            }

            notifyPluginsContextChanged(modifiedKeys)

            ContextUpdateResult.Accepted
        } else {
            logger.debug { "Found potential conflict in update request. Rejected items: $rejectedItems." }

            ContextUpdateResult.Rejected(
                rejectedItems.mapValues { (key, _) ->
                    context[key]?.let {
                        ContextUpdateResult.Rejected.Reason.Updated(it.toDto())
                    } ?: ContextUpdateResult.Rejected.Reason.Removed
                }.mapKeys { it.key.qualifiedName }
            )
        }
    }
    private fun getKeyOrUnsupported(qualifiedName: String): Session.Context.Key<*> {
        return sessionContextKeyRegistry.getKeyByQualifiedName(qualifiedName) ?: UnsupportedKey(qualifiedName)
    }

    private fun <T: Any> deserializeAndPut(key: Session.Context.Key<T>, itemDto: ContextItemDto) {
        val item = when {
            key is UnsupportedKey -> {
                Session.Context.Item(key, itemDto.revision, itemDto.value)
            }
            itemDto.value.format == payloadSerializer.format -> {
                Session.Context.Item(key, itemDto.revision, payloadSerializer.deserialize(key.serializer, itemDto.value))
            }
            payloadSerializerFactory.supportedSerializationFormats.contains(itemDto.value.format) -> {
                Session.Context.Item(key, itemDto.revision, payloadSerializerFactory.create(itemDto.value.format).deserialize(key.serializer, itemDto.value))
            }
            else -> {
                Session.Context.Item(UnsupportedKey(key.qualifiedName), itemDto.revision, itemDto.value)
            }
        }
        putItem(item)
    }

    private fun <T: Any> putItem(item: Session.Context.Item<T>) {
        context[item.key] = item
    }

    private fun <T: Any> Session.Context.Item<T>.toDto(): ContextItemDto {
        val serializedValue = if (key is UnsupportedKey) {
            value as SerializedPayload
        } else {
            payloadSerializer.serialize(key.serializer, value)
        }
        return ContextItemDto(revision, serializedValue)
    }

    private suspend fun notifyPluginsContextChanged(modifiedKeys: Set<Session.Context.Key<*>>) {
        for (plugin in plugins) {
            plugin.onContextChanged(this, modifiedKeys)
        }
    }

    override fun <VALUE: Any> get(key: Session.Context.Key<VALUE>): VALUE? {
        return context[key]?.value
    }

    override suspend fun contextTransaction(block: Session.Context.Mutator.() -> Unit) {
        val ourJob = Job()

        // Before running the transaction, we want to make sure there's no other context sync running.
        awaitCompletedContextSync()
        runningContextUpdate = ourJob

        val modifiedKeys = mutableSetOf<Session.Context.Key<*>>()
        // TODO: Don't rely on number of retries, but check the result from the other party to detect a bug.
        var rejections = 0
        do {
            val modifications = mutableMapOf<Session.Context.Key<Any>, Session.Context.Mutator.Action>()
            val mutator = Session.Context.Mutator(context, modifications)

            block(mutator)

            val request = ContextUpdateRequest(
                modifications.mapValues { (_, action) ->
                    when (action) {
                        is Session.Context.Mutator.Action.Required -> ContextUpdateRequest.Modification.Required(action.oldItem?.revision)
                        is Session.Context.Mutator.Action.Set -> ContextUpdateRequest.Modification.Set(
                            action.oldItem?.revision,
                            action.newItem.toDto()
                        )
                        is Session.Context.Mutator.Action.Remove -> ContextUpdateRequest.Modification.Remove(action.oldItem.revision)
                    }
                }.mapKeys { it.key.qualifiedName }
            )

            logger.debug { "Will try updating context (try #${rejections + 1}) with $request." }
            val result = client.update(request)

            contextModificationLock.withLock {
                when (result) {
                    is ContextUpdateResult.Rejected -> {
                        rejections += 1
                        if (rejections >= maximumRejections) {
                            // FIXME: Throw error and inform user
                        }
                        logger.debug { "Context update rejected (try #$rejections). Reasons: ${result.rejectedModifications}" }
                        val modificationsWithKeys = result.rejectedModifications.mapKeys { getKeyOrUnsupported(it.key) }
                        for ((key, reason) in modificationsWithKeys) {
                            Do exhaustive when (reason) {
                                ContextUpdateResult.Rejected.Reason.Removed -> {
                                    context.remove(key)
                                    modifiedKeys.add(key)
                                }
                                is ContextUpdateResult.Rejected.Reason.Updated -> {
                                    deserializeAndPut(key, reason.newItem)
                                    modifiedKeys.add(key)
                                }
                            }
                        }
                    }
                    ContextUpdateResult.Accepted -> {
                        logger.debug { "Context update accepted (try #$rejections). Saving to local context." }
                        for ((key, action) in modifications) {
                            Do exhaustive when (action) {
                                is Session.Context.Mutator.Action.Required -> continue
                                is Session.Context.Mutator.Action.Set -> {
                                    @Suppress("UNCHECKED_CAST")
                                    context[key] = action.newItem as Session.Context.Item<Any>
                                    modifiedKeys.add(key)
                                }
                                is Session.Context.Mutator.Action.Remove -> {
                                    context.remove(key)
                                    modifiedKeys.add(key)
                                }
                            }
                        }
                    }
                }
            }
        } while (result != ContextUpdateResult.Accepted && rejections < maximumRejections)

        notifyPluginsContextChanged(modifiedKeys)

        runningContextUpdate = null
        ourJob.complete()
    }

    override suspend fun <PAYLOAD, RESPONSE> interceptIncomingSingleCall(
        payload: PAYLOAD,
        call: RunnableCallDescription.Single<PAYLOAD, RESPONSE>,
        next: suspend (PAYLOAD) -> RESPONSE,
    ): RESPONSE = withSessionIfNeeded(call) {
        super.interceptIncomingSingleCall(payload, call, next)
    }

    override suspend fun <PAYLOAD, CLIENT_STREAM, RESPONSE> interceptIncomingUpstreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: RunnableCallDescription.ColdUpstream<PAYLOAD, CLIENT_STREAM, RESPONSE>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> RESPONSE,
    ): RESPONSE = withSessionIfNeeded(call) {
        super.interceptIncomingUpstreamCall(payload, stream, call, next)
    }

    override suspend fun <PAYLOAD, SERVER_STREAM> interceptIncomingDownstreamCall(
        payload: PAYLOAD,
        call: RunnableCallDescription.ColdDownstream<PAYLOAD, SERVER_STREAM>,
        next: suspend (PAYLOAD) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> = withSessionIfNeeded(call) {
        super.interceptIncomingDownstreamCall(payload, call, next)
    }

    override suspend fun <PAYLOAD, CLIENT_STREAM, SERVER_STREAM> interceptIncomingBistreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: RunnableCallDescription.ColdBistream<PAYLOAD, CLIENT_STREAM, SERVER_STREAM>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> = withSessionIfNeeded(call) {
        super.interceptIncomingBistreamCall(payload, stream, call, next)
    }

    @OptIn(ExperimentalContracts::class)
    private suspend fun <RESULT> withSessionIfNeeded(call: RunnableCallDescription<*>, block: suspend () -> RESULT): RESULT {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        // We don't want to inject the service into the coroutine context if it's the CoroutineSyncService's call.
        return if (call.identifier.serviceId == ContextUpdateService.Descriptor.identifier) {
            block()
        } else {
            // TODO: Replace `this` with `immutableCopy`
            withContext(coroutineContext + this) {
                val result = block()
                awaitCompletedContextSync()
                result
            }
        }
    }

    override suspend fun <PAYLOAD, RESPONSE> interceptOutgoingSingleCall(
        payload: PAYLOAD,
        call: SingleCallDescription<PAYLOAD, RESPONSE>,
        next: suspend (PAYLOAD) -> RESPONSE,
    ): RESPONSE = withCompletedContextSyncIfNeeded(call) {
        super.interceptOutgoingSingleCall(payload, call, next)
    }

    override suspend fun <PAYLOAD, CLIENT_STREAM, RESPONSE> interceptOutgoingUpstreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: ColdUpstreamCallDescription<PAYLOAD, CLIENT_STREAM, RESPONSE>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> RESPONSE,
    ): RESPONSE = withCompletedContextSyncIfNeeded(call) {
        super.interceptOutgoingUpstreamCall(payload, stream, call, next)
    }

    override suspend fun <PAYLOAD, SERVER_STREAM> interceptOutgoingDownstreamCall(
        payload: PAYLOAD,
        call: ColdDownstreamCallDescription<PAYLOAD, SERVER_STREAM>,
        next: suspend (PAYLOAD) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> = withCompletedContextSyncIfNeeded(call) {
        super.interceptOutgoingDownstreamCall(payload, call, next)
    }

    override suspend fun <PAYLOAD, CLIENT_STREAM, SERVER_STREAM> interceptOutgoingBistreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: ColdBistreamCallDescription<PAYLOAD, CLIENT_STREAM, SERVER_STREAM>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> = withCompletedContextSyncIfNeeded(call) {
        super.interceptOutgoingBistreamCall(payload, stream, call, next)
    }

    private suspend fun <RESULT> withCompletedContextSyncIfNeeded(call: CallDescription<*>, block: suspend () -> RESULT): RESULT {
        // We can't wait for the context to sync if it's the ContextSyncService's call.
        return if (call.identifier.serviceId == ContextUpdateService.Descriptor.identifier) {
            block()
        } else {
            awaitCompletedContextSync()
            block()
        }
    }

    private suspend fun awaitCompletedContextSync() {
        runningContextUpdate?.join()
    }
}

class RPCContribution<T: Any>(
    val contribution: T,
    contributionClass: KClass<T>,
): CoroutineContext.Element {
    data class Key<T: Any>(val contributionClass: KClass<T>): CoroutineContext.Key<RPCContribution<T>>

    override val key: CoroutineContext.Key<*> = Key(contributionClass)
}

suspend fun <RESULT> withContributed(module: SerializersModule, block: suspend () -> RESULT): RESULT {
    return withContribution(module, SerializersModule::class, block)
}

internal fun <T: Any> CoroutineContext.contribution(contributionClass: KClass<T>): T? {
    return get(RPCContribution.Key(contributionClass))?.contribution
}

internal suspend inline fun <reified T: Any> contextContribution(): T? {
    return coroutineContext.contribution(T::class)
}

internal suspend fun <T: Any, RESULT> withContribution(contribution: T, contributionClass: KClass<T>, block: suspend () -> RESULT): RESULT {
    return withContext(coroutineContext + RPCContribution(contribution, contributionClass)) {
        block()
    }
}

@Serializable
class ContextItemDto(
    val revision: Int,
    val value: SerializedPayload,
)

typealias KeyDto = String

@Serializable
class ContextUpdateRequest(
    val modifications: Map<KeyDto, Modification> = emptyMap(),
) {
    @Serializable
    sealed class Modification {
        abstract val oldRevisionOrNull: Int?

        @Serializable
        class Required(val oldRevision: Int? = null): Modification() {
            override val oldRevisionOrNull: Int?
                get() = oldRevision
        }

        @Serializable
        class Set(val oldRevision: Int? = null, val newItem: ContextItemDto): Modification() {
            override val oldRevisionOrNull: Int?
                get() = oldRevision
        }
        @Serializable
        class Remove(val oldRevision: Int): Modification() {
            override val oldRevisionOrNull: Int?
                get() = oldRevision
        }
    }
}

@Serializable
sealed class ContextUpdateResult {
    @Serializable
    object Accepted: ContextUpdateResult()
    @Serializable
    class Rejected(
        val rejectedModifications: Map<KeyDto, Reason> = emptyMap(),
    ): ContextUpdateResult() {
        @Serializable
        sealed class Reason {
            @Serializable
            object Removed: Reason()
            @Serializable
            class Updated(val newItem: ContextItemDto): Reason()
        }
    }
}

interface ContextUpdateService {
    suspend fun update(request: ContextUpdateRequest): ContextUpdateResult

    class Client(
        private val transport: RPCTransport,
    ): ContextUpdateService {
        override suspend fun update(request: ContextUpdateRequest): ContextUpdateResult {
            return transport.singleCall(Descriptor.Call.update, request)
        }
    }

    object Descriptor: ServiceDescriptor<ContextUpdateService> {
        const val identifier = "builtin:hyperdrive.ContextSyncService"

        override fun describe(service: ContextUpdateService): ServiceDescription {
            return ServiceDescription(
                identifier,
                listOf(
                    Call.update.calling { request ->
                        service.update(request)
                    }
                )
            )
        }

        object Call {
            val update = SingleCallDescription(
                ServiceCallIdentifier(identifier, "update"),
                ContextUpdateRequest.serializer(),
                ContextUpdateResult.serializer(),
                RPCErrorSerializer(),
            )
        }
    }
}

data class UnsupportedKey(override val qualifiedName: String): Session.Context.Key<SerializedPayload> {
    override val serializer: KSerializer<SerializedPayload> = SerializedPayload.serializer()
}
