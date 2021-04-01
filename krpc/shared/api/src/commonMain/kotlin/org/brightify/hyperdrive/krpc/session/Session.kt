package org.brightify.hyperdrive.krpc.session

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass

suspend inline fun <T> withSession(block: suspend Session.() -> T): T {
    return coroutineContext.rpcSession.block()
}

val CoroutineContext.rpcSession: Session
    get() = get(Session) ?: error("Current context doesn't contain Session in it: $this")

interface Session: CoroutineContext.Element {
    public companion object Key: CoroutineContext.Key<Session>

    operator fun <VALUE: Any> get(key: Context.Key<VALUE>): VALUE?

    operator fun iterator(): Iterator<Context.Item<*>>

    fun copyOfContext(): Context

    suspend fun contextTransaction(block: Context.Mutator.() -> Unit)

    object Id: Context.Key<Long> {
        override val qualifiedName = "builtin:org.brightify.hyperdrive.krpc.api.Session.Id"
        override val serializer = Long.serializer()
    }

    class Context(
        @PublishedApi
        internal val data: MutableMap<Key<*>, Item<*>>,
    ) {
        operator fun <VALUE: Any> get(key: Key<VALUE>): Item<VALUE>? = data[key] as? Item<VALUE>

        operator fun <VALUE: Any> set(key: Key<VALUE>, item: Item<VALUE>) {
            data[key] = item
        }

        fun <VALUE: Any> remove(key: Key<VALUE>): Item<VALUE>? = data.remove(key) as? Item<VALUE>

        public inline operator fun iterator(): Iterator<Item<*>> = data.values.iterator()

        fun copy(): Context {
            return Context(
                data.toMutableMap()
            )
        }

        interface Key<VALUE: Any> {
            val qualifiedName: String
            val serializer: KSerializer<VALUE>
        }

        class Item<T: Any>(
            val key: Key<T>,
            val revision: Int,
            val value: T,
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

            sealed class Action {
                class Required(val oldItem: Item<*>?): Action()
                class Set(val oldItem: Item<*>?, val newItem: Item<*>): Action()
                class Remove(val oldItem: Item<*>): Action()
            }

            public operator fun <VALUE: Any> get(key: Key<VALUE>): VALUE? {
                val oldItem = oldContext[key]
                if (!modifications.containsKey(key as Key<Any>)) {
                    modifications[key] = Action.Required(oldItem)
                }
                return oldItem?.value
            }

            public operator fun <VALUE: Any> set(key: Key<VALUE>, newValue: VALUE) {
                val oldItem = oldContext[key]
                val newRevision = oldItem?.let { it.revision + 1 } ?: Int.MIN_VALUE

                modifications[key as Key<Any>] = Action.Set(oldItem, Item(key, newRevision, newValue))
            }

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