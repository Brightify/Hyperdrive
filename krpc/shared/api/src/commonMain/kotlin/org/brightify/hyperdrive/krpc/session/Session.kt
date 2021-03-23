package org.brightify.hyperdrive.krpc.session

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.reflect.KClass

interface Session {
    suspend fun contextTransaction(block: Context.Mutator.() -> Unit)

    object Id: Context.Key<Long> {
        override val qualifiedName = "builtin:org.brightify.hyperdrive.krpc.api.Session.Id"
        override val serializer = Long.serializer()
    }

    class Context(
        val data: MutableMap<Key<*>, Any>,
    ) {
        internal fun <VALUE: Any> get(key: Key<VALUE>): VALUE? = data[key] as? VALUE

        internal fun <VALUE: Any> put(value: VALUE, key: Key<VALUE>) {
            data[key] = value
        }

        interface Key<VALUE: Any> {
            val qualifiedName: String
            val serializer: KSerializer<VALUE>
        }

        public class Mutator(
            private val oldContext: Context,
        ) {
            internal val modifications = mutableMapOf<Key<*>, Action>()

            internal sealed class Action {
                class Set(val oldValue: Any?, val newValue: Any): Action()
                class Remove(val oldValue: Any?): Action()
            }

            public fun <VALUE: Any> get(key: Key<VALUE>): VALUE? {
                return oldContext.get(key)
            }

            public fun <VALUE: Any> set(newValue: VALUE, key: Key<VALUE>) {
                modifications[key] = Action.Set(oldContext.get(key), newValue)
            }

            public fun <VALUE: Any> remove(key: Key<VALUE>) {
                modifications[key] = Action.Remove(oldContext.get(key))
            }
        }
    }
}