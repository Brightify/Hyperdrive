package org.brightify.hyperdrive.krpc.extension

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.extension.session.DefaultSession
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.application.RPCNode
import org.brightify.hyperdrive.krpc.application.RPCNodeExtension
import org.brightify.hyperdrive.krpc.description.*
import org.brightify.hyperdrive.krpc.application.PayloadSerializer
import org.brightify.hyperdrive.krpc.extension.session.ContextUpdateRequest
import org.brightify.hyperdrive.krpc.extension.session.ContextUpdateResult
import org.brightify.hyperdrive.krpc.extension.session.ContextUpdateService
import org.brightify.hyperdrive.krpc.session.Session
import org.brightify.hyperdrive.krpc.session.SessionContextKeyRegistry
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass

public class SessionNodeExtension internal constructor(
    session: DefaultSession,
    private val plugins: List<Plugin>,
): ContextUpdateService, RPCNodeExtension {
    public companion object {
        public val logger: Logger = Logger<SessionNodeExtension>()
        public const val maximumRejections: Int = 10
    }

    public object Identifier: RPCNodeExtension.Identifier<SessionNodeExtension> {
        override val uniqueIdentifier: String = "builtin:Session"
        override val extensionClass: KClass<SessionNodeExtension> = SessionNodeExtension::class
    }

    public interface Plugin {
        public suspend fun onBindComplete(session: Session) { }

        public suspend fun onContextChanged(session: Session, modifiedKeys: Set<Session.Context.Key<*>>) { }
    }

    public class Factory(
        private val sessionContextKeyRegistry: SessionContextKeyRegistry,
        private val payloadSerializerFactory: PayloadSerializer.Factory,
        private val plugins: List<Plugin> = emptyList(),
    ): RPCNodeExtension.Factory<SessionNodeExtension> {
        override val identifier: Identifier = Identifier

        override val isRequiredOnOtherSide: Boolean = true

        override fun create(): SessionNodeExtension {
            return SessionNodeExtension(
                DefaultSession(payloadSerializerFactory, sessionContextKeyRegistry),
                plugins
            )
        }
    }

    private val _session: DefaultSession = session
    public val session: Session = _session

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

    override suspend fun whileConnected(): Unit = coroutineScope {
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

    override suspend fun clear(): Unit = _session.clear()

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

