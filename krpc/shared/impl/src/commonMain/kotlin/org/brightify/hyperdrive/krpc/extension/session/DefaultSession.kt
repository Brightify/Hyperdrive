package org.brightify.hyperdrive.krpc.extension.session

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.application.RPCNode
import org.brightify.hyperdrive.krpc.extension.SessionNodeExtension
import org.brightify.hyperdrive.krpc.application.PayloadSerializer
import org.brightify.hyperdrive.krpc.session.Session
import org.brightify.hyperdrive.krpc.session.SessionContextKeyRegistry
import org.brightify.hyperdrive.utils.Do
import kotlin.coroutines.CoroutineContext

public class DefaultSession internal constructor(
    private val payloadSerializerFactory: PayloadSerializer.Factory,
    private val sessionContextKeyRegistry: SessionContextKeyRegistry,
): Session {

    override val key: CoroutineContext.Key<*> get() = Session
    private val context = Session.Context(mutableMapOf())
    private val contextModificationLock = Mutex()
    // Keys modified during context transactions.
    private val modifiedKeysFlow = MutableSharedFlow<Set<Session.Context.Key<*>>>()
    // Modified keys ready to be observed.
    private val modifiedKeysForObserve = MutableSharedFlow<Set<Session.Context.Key<*>>>()
    private var runningContextUpdate: Job? = null

    private lateinit var contractPayloadSerializer: PayloadSerializer
    private lateinit var client: ContextUpdateService.Client

    public suspend fun bind(transport: RPCTransport, contract: RPCNode.Contract) {
        contractPayloadSerializer = contract.payloadSerializer
        client = ContextUpdateService.Client(transport)
    }

    public suspend fun whileConnected(): Unit = coroutineScope {
        /*
         * The observing code can be slower to collect the keys, so we need to buffer all modified keys to a set that can be consumed by the
         * observers later. As a benefit emitters to modifiedKeysFlow can continue without waiting for the observers. This is important
         * to avoid deadlocks when the context is updated and one of the observers triggers a context transaction.
         */
        val lock = Mutex()
        val trigger = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        var modifiedKeysToNotify = mutableSetOf<Session.Context.Key<*>>()

        launch {
            // Collect keys modified during context transactions.
            modifiedKeysFlow.collect {
                lock.withLock {
                    // Add modified keys to the current buffer.
                    modifiedKeysToNotify.addAll(it)
                }
                // Notify the keys are ready to collect.
                trigger.tryEmit(Unit)
            }
        }

        launch {
            // When keys are ready to collect ...
            trigger.collect {
                // .. consume the modified keys buffer.
                val keysToNotify = lock.withLock {
                    val keysToNotify = modifiedKeysToNotify
                    modifiedKeysToNotify = mutableSetOf()
                    keysToNotify
                }

                // Notify all observers and wait for them to complete.
                modifiedKeysForObserve.emit(keysToNotify)
            }
        }
    }

    override fun <VALUE: Any> get(key: Session.Context.Key<VALUE>): VALUE? {
        return context[key]?.value
    }

    override fun <VALUE : Any> observe(key: Session.Context.Key<VALUE>): Flow<VALUE?> {
        return modifiedKeysForObserve
            .flatMapConcat {
                if (it.contains(key)) {
                    flowOf(context[key]?.value)
                } else {
                    emptyFlow()
                }
            }
            .onStart {
                emit(context[key]?.value)
            }
    }

    override fun iterator(): Iterator<Session.Context.Item<*>> = context.iterator()

    override fun copyOfContext(): Session.Context = context.copy()

    override fun observeContextSnapshots(): Flow<Session.Context> = modifiedKeysForObserve.map {
        context.copy()
    }

    override fun observeModifications(): Flow<Set<Session.Context.Key<*>>> = modifiedKeysForObserve.onStart {
        emit(sessionContextKeyRegistry.allKeys.toSet() + context.keys)
    }

    override suspend fun contextTransaction(block: Session.Context.Mutator.() -> Unit) {
        val ourJob = Job()

        // Before running the transaction, we want to make sure there's no other context sync running.
        awaitCompletedContextSync()

        val modifiedKeys = mutableSetOf<Session.Context.Key<*>>()
        try {
            runningContextUpdate = ourJob
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
                                action.newItem.toDto(),
                            )
                            is Session.Context.Mutator.Action.Remove -> ContextUpdateRequest.Modification.Remove(action.oldItem.revision)
                        }
                    }.mapKeys { it.key.qualifiedName }
                )

                SessionNodeExtension.logger.debug { "Will try updating context (try #${rejections + 1}) with $request." }
                val result = client.update(request)

                contextModificationLock.withLock {
                    when (result) {
                        is ContextUpdateResult.Rejected -> {
                            rejections += 1
                            SessionNodeExtension.logger.debug { "Context update rejected (try #$rejections). Reasons: ${result.rejectedModifications}" }
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
                            if (rejections >= SessionNodeExtension.maximumRejections) {
                                throw SessionContextTransactionFailedException(result.rejectedModifications,
                                    SessionNodeExtension.maximumRejections)
                            }
                        }
                        ContextUpdateResult.Accepted -> {
                            SessionNodeExtension.logger.debug { "Context update accepted (try #${rejections + 1}). Saving to local context." }
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
            } while (result != ContextUpdateResult.Accepted)

            modifiedKeysFlow.emit(modifiedKeys)
            ourJob.complete()
        } catch (t: Throwable) {
            modifiedKeysFlow.emit(modifiedKeys)
            ourJob.completeExceptionally(t)
            throw t
        } finally {
            runningContextUpdate = null
        }
    }

    override suspend fun clearContext() {
        val ourJob = Job()

        awaitCompletedContextSync()

        try {
            runningContextUpdate = ourJob
            client.clear()

            val modifiedKeys = contextModificationLock.withLock {
                val modifiedKeys = context.keys
                context.clear()
                modifiedKeys
            }

            modifiedKeysFlow.emit(modifiedKeys)

            ourJob.complete()
        } catch (t: Throwable) {
            ourJob.completeExceptionally(t)
            throw t
        } finally {
            runningContextUpdate = null
        }
    }

    public suspend fun update(request: ContextUpdateRequest): ContextUpdateResult {
        val (modifiedKeys, result) = contextModificationLock.withLock {
            SessionNodeExtension.logger.debug { "Received a session context update request: $request." }
            val modificationsWithKeys = request.modifications.mapKeys { getKeyOrUnsupported(it.key) }
            val rejectedItems = modificationsWithKeys.filter { (key, modification) ->
                modification.oldRevisionOrNull != context[key]?.revision
            }

            if (rejectedItems.isEmpty()) {
                SessionNodeExtension.logger.debug { "No reason for a rejection found. Accepting." }

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
                modifiedKeys to ContextUpdateResult.Accepted
            } else {
                SessionNodeExtension.logger.debug { "Found potential conflict in update request. Rejected items: $rejectedItems." }

                null to ContextUpdateResult.Rejected(
                    rejectedItems.mapValues { (key, _) ->
                        context[key]?.let {
                            ContextUpdateResult.Rejected.Reason.Updated(it.toDto())
                        } ?: ContextUpdateResult.Rejected.Reason.Removed
                    }.mapKeys { it.key.qualifiedName }
                )
            }
        }

        if (modifiedKeys != null) {
            modifiedKeysFlow.emit(modifiedKeys)
        }

        return result
    }

    public suspend fun clear() {
        val modifiedKeys = contextModificationLock.withLock {
            SessionNodeExtension.logger.debug { "Received a session context clear request." }
            val modifiedKeys = context.keys.toSet()
            context.clear()
            modifiedKeys
        }

        modifiedKeysFlow.emit(modifiedKeys)
    }

    override suspend fun awaitCompletedContextSync() {
        runningContextUpdate?.join()
    }

    private fun getKeyOrUnsupported(qualifiedName: String): Session.Context.Key<*> {
        return sessionContextKeyRegistry.getKeyByQualifiedName(qualifiedName) ?: UnsupportedKey(qualifiedName)
    }

    private fun <T: Any> deserializeAndPut(key: Session.Context.Key<T>, itemDto: ContextItemDto) {
        val item = when {
            key is UnsupportedKey -> {
                Session.Context.Item(key, itemDto.revision, itemDto.value)
            }
            itemDto.value.format == contractPayloadSerializer.format -> {
                Session.Context.Item(key, itemDto.revision, contractPayloadSerializer.deserialize(key.serializer, itemDto.value))
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
            contractPayloadSerializer.serialize(key.serializer, value)
        }
        return ContextItemDto(revision, serializedValue)
    }
}
