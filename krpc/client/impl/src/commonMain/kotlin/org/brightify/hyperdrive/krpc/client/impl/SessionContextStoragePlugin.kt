package org.brightify.hyperdrive.krpc.client.impl

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.SessionNodeExtension
import org.brightify.hyperdrive.krpc.UnsupportedKey
import org.brightify.hyperdrive.krpc.protocol.ascension.PayloadSerializer
import org.brightify.hyperdrive.krpc.session.Session
import org.brightify.hyperdrive.krpc.session.SessionContextKeyRegistry

class SessionContextSnapshotPlugin: SessionNodeExtension.Plugin {
    private val listeners = mutableListOf<Listener<*>>()
    private var latestContextSnapshot: Session.Context? = null

    fun <VALUE: Any> observe(key: Session.Context.Key<VALUE>, includeInitialValue: Boolean = false): Flow<VALUE?> = flow {
        val channel = Channel<VALUE?>()

        val listener = FlowListener(key, channel)

        val contextSnapshot = latestContextSnapshot
        if (contextSnapshot != null) {
            emit(contextSnapshot[key]?.value)
        }

        emitAll(
            channel.receiveAsFlow()
                .onStart {
                    registerListener(listener)
                }
                .onCompletion {
                    unregisterListener(listener)
                }
        )
    }

    fun <VALUE: Any> registerListener(listener: Listener<VALUE>) {
        listeners.add(listener)
    }

    fun <VALUE: Any> unregisterListener(listener: Listener<VALUE>) {
        listeners.remove(listener)
    }

    override suspend fun onBindComplete(session: Session) {
        latestContextSnapshot = session.copyOfContext()
    }

    override suspend fun onContextChanged(session: Session, modifiedKeys: Set<Session.Context.Key<*>>) {
        val contextSnapshot = session.copyOfContext()
        latestContextSnapshot = contextSnapshot
        for (listener in listeners) {
            if (modifiedKeys.contains(listener.key)) {
                listener.notify(contextSnapshot)
            }
        }
    }

    private suspend fun <VALUE: Any> Listener<VALUE>.notify(context: Session.Context) {
        onValueChanged(context[key]?.value)
    }

    interface Listener<VALUE: Any> {
        val key: Session.Context.Key<VALUE>

        suspend fun onValueChanged(value: VALUE?)
    }

    private class FlowListener<VALUE: Any>(
        override val key: Session.Context.Key<VALUE>,
        private val channel: Channel<VALUE?>,
    ): Listener<VALUE> {
        override suspend fun onValueChanged(value: VALUE?) {
            channel.send(value)
        }
    }
}

class SessionContextStoragePlugin(
    private val storage: Storage,
    private val payloadSerializerFactory: PayloadSerializer.Factory,
    private val sessionContextKeyRegistry: SessionContextKeyRegistry,
): SessionNodeExtension.Plugin {
    private val serializationStrategy = ListSerializer(SerializedItem.serializer())

    @Serializable
    class SerializedItem(
        val keyQualifiedName: String,
        val revision: Int,
        val value: SerializedPayload,
    )

    interface Storage {
        suspend fun restore(): SerializedPayload?

        suspend fun store(context: SerializedPayload)
    }

    override suspend fun onBindComplete(session: Session) {
        val restoredItems = storage.restore() ?: return
        val deserializedItems = payloadSerializerFactory.deserialize(serializationStrategy, restoredItems)
        val resolvedItems = deserializedItems.map { dto ->
            val key = sessionContextKeyRegistry.getKeyByQualifiedName(dto.keyQualifiedName)
            if (key != null) {
                deserialize(key, dto)
            } else {
                Session.Context.Item(UnsupportedKey(dto.keyQualifiedName), dto.revision, dto.value)
            }
        }

        session.contextTransaction {
            for (item in resolvedItems) {
                this.putItem(item)
            }
        }
    }

    override suspend fun onContextChanged(session: Session, modifiedKeys: Set<Session.Context.Key<*>>) {
        val serializedItems = session.iterator().asSequence().map { item ->
            item.serialize()
        }

        storage.store(payloadSerializerFactory.serialize(serializationStrategy, serializedItems.toList()))
    }

    private fun <T: Any> deserialize(key: Session.Context.Key<T>, dto: SerializedItem): Session.Context.Item<T> {
        val value = payloadSerializerFactory.deserialize(key.serializer, dto.value)
        return Session.Context.Item(key, dto.revision, value)
    }

    private fun <T: Any> Session.Context.Item<T>.serialize(): SerializedItem {
        return SerializedItem(key.qualifiedName, revision, payloadSerializerFactory.serialize(key.serializer, value))
    }

    private fun <T: Any> Session.Context.Mutator.putItem(item: Session.Context.Item<T>) {
        this[item.key] = item.value
    }
}