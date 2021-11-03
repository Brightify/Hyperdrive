package org.brightify.hyperdrive.krpc.extension

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.modules.SerializersModule
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.extension.session.DefaultSession
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.application.RPCNode
import org.brightify.hyperdrive.krpc.application.RPCNodeExtension
import org.brightify.hyperdrive.krpc.description.*
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.protocol.ascension.PayloadSerializer
import org.brightify.hyperdrive.krpc.session.Session
import org.brightify.hyperdrive.krpc.session.SessionContextKeyRegistry
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass

class SessionNodeExtension internal constructor(
    session: DefaultSession,
    private val plugins: List<Plugin>,
): ContextUpdateService, RPCNodeExtension {
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
            return SessionNodeExtension(
                DefaultSession(payloadSerializerFactory, sessionContextKeyRegistry),
                plugins
            )
        }
    }

    private val _session: DefaultSession = session
    val session: Session = _session

    override val providedServices: List<ServiceDescription> = listOf(
        ContextUpdateService.Descriptor.describe(this)
    )

    private val modifiedKeysFlow = MutableSharedFlow<Set<Session.Context.Key<*>>>()

    override suspend fun bind(transport: RPCTransport, contract: RPCNode.Contract) {
        _session.bind(transport, contract)

        for (plugin in plugins) {
            plugin.onBindComplete(session)
        }
    }

    override suspend fun whileConnected() = coroutineScope {
        launch {
            _session.whileConnected()
        }

        session.observeModifications()
            .collect {
                notifyPluginsContextChanged(it)
            }
    }

    override suspend fun enhanceParallelWorkContext(context: CoroutineContext): CoroutineContext {
        return context + session
    }

    override suspend fun update(request: ContextUpdateRequest): ContextUpdateResult = _session.update(request)

    override suspend fun clear() = _session.clear()

    private suspend fun notifyPluginsContextChanged(modifiedKeys: Set<Session.Context.Key<*>>) {
        modifiedKeysFlow.emit(modifiedKeys)

        for (plugin in plugins) {
            plugin.onContextChanged(session, modifiedKeys)
        }
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
            // TODO: Replace `session` with an immutable copy
            withContext(coroutineContext + session) {
                val result = block()
                session.awaitCompletedContextSync()
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
            session.awaitCompletedContextSync()
            block()
        }
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

    suspend fun clear()

    class Client(
        private val transport: RPCTransport,
    ): ContextUpdateService {
        override suspend fun update(request: ContextUpdateRequest): ContextUpdateResult {
            return transport.singleCall(Descriptor.Call.update, request)
        }

        override suspend fun clear() {
            return transport.singleCall(Descriptor.Call.clear, Unit)
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
                    },
                    Call.clear.calling { reequest ->
                        service.clear()
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

            val clear = SingleCallDescription(
                ServiceCallIdentifier(identifier, "clear"),
                Unit.serializer(),
                Unit.serializer(),
                RPCErrorSerializer(),
            )
        }
    }
}

data class UnsupportedKey(override val qualifiedName: String): Session.Context.Key<SerializedPayload> {
    override val serializer: KSerializer<SerializedPayload> = SerializedPayload.serializer()
}
