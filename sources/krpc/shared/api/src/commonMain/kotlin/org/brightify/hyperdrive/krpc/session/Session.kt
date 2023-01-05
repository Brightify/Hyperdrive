package org.brightify.hyperdrive.krpc.session

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

public suspend inline fun <T> withSession(block: Session.() -> T): T {
    return coroutineContext.rpcSession.block()
}

public val CoroutineContext.rpcSession: Session
    get() = get(Session) ?: error("Current context doesn't contain Session in it: $this")

public interface Session: CoroutineContext.Element {
    public companion object Key: CoroutineContext.Key<Session>

    public operator fun <VALUE: Any> get(key: Context.Key<VALUE>): VALUE?

    public fun <VALUE: Any> observe(key: Context.Key<VALUE>): Flow<VALUE?>

    public operator fun iterator(): Iterator<Context.Item<*>>

    public fun copyOfContext(): Context

    public fun observeContextSnapshots(): Flow<Context>

    public fun observeModifications(): Flow<Set<Context.Key<*>>>

    public suspend fun contextTransaction(block: Context.Mutator.() -> Unit)

    public suspend fun clearContext()

    public suspend fun awaitCompletedContextSync()

    public object Id: Context.Key<Long> {
        override val qualifiedName: String = "builtin:org.brightify.hyperdrive.krpc.api.Session.Id"
        override val serializer: KSerializer<Long> = Long.serializer()
    }

    public class Context(
        @PublishedApi
        internal val data: MutableMap<Key<*>, Item<*>>,
    ) {
        public val keys: Set<Key<*>>
            get() = data.keys

        @Suppress("UNCHECKED_CAST")
        public operator fun <VALUE: Any> get(key: Key<VALUE>): Item<VALUE>? = data[key] as? Item<VALUE>

        public operator fun <VALUE: Any> set(key: Key<VALUE>, item: Item<VALUE>) {
            data[key] = item
        }

        @Suppress("UNCHECKED_CAST")
        public fun <VALUE: Any> remove(key: Key<VALUE>): Item<VALUE>? = data.remove(key) as? Item<VALUE>

        public fun clear(): Unit = data.clear()

        public operator fun iterator(): Iterator<Item<*>> = data.values.iterator()

        public fun copy(): Context {
            return Context(
                data.toMutableMap()
            )
        }

        public interface Key<VALUE: Any> {
            public val qualifiedName: String
            public val serializer: KSerializer<VALUE>
        }

        public class Item<T: Any>(
            public val key: Key<T>,
            public val revision: Int,
            public val value: T,
        ) {
            override fun equals(other: Any?): Boolean {
                return if (other is Item<*>) {
                    other.key.qualifiedName == key.qualifiedName
                } else {
                    false
                }
            }

            override fun hashCode(): Int {
                return key.qualifiedName.hashCode()
            }
        }

        public class Mutator(
            private val oldContext: Context,
            // Required to be passed in so the caller can get the values, but not the user who has an instance of Mutator.
            private val modifications: MutableMap<Key<Any>, Action>,
        ) {

            public sealed class Action {
                public class Required(public val oldItem: Item<*>?): Action()
                public class Set(public val oldItem: Item<*>?, public val newItem: Item<*>): Action()
                public class Remove(public val oldItem: Item<*>): Action()
            }

            public operator fun <VALUE: Any> get(key: Key<VALUE>): VALUE? {
                val oldItem = oldContext[key]
                @Suppress("UNCHECKED_CAST")
                if (!modifications.containsKey(key as Key<Any>)) {
                    modifications[key] = Action.Required(oldItem)
                }
                return oldItem?.value
            }

            public operator fun <VALUE: Any> set(key: Key<VALUE>, newValue: VALUE) {
                val oldItem = oldContext[key]
                val newRevision = oldItem?.let { it.revision + 1 } ?: Int.MIN_VALUE

                @Suppress("UNCHECKED_CAST")
                modifications[key as Key<Any>] = Action.Set(oldItem, Item(key, newRevision, newValue))
            }

            @Suppress("UNCHECKED_CAST")
            public fun <VALUE: Any> remove(key: Key<VALUE>) {
                val oldItem = oldContext[key]
                if (oldItem != null) {
                    modifications[key as Key<Any>] = Action.Remove(oldItem)
                } else if (modifications[key as Key<Any>] !is Action.Required) {
                    modifications.remove(key as Key<Any>)
                }
            }
        }
    }
}