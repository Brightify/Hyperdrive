package org.brightify.hyperdrive.krpc.client.impl

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.SessionNodeExtension
import org.brightify.hyperdrive.krpc.UnsupportedKey
import org.brightify.hyperdrive.krpc.protocol.ascension.PayloadSerializer
import org.brightify.hyperdrive.krpc.session.Session
import org.brightify.hyperdrive.krpc.session.SessionContextKeyRegistry

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

    override suspend fun onContextChanged(session: Session) {
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