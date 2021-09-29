package org.brightify.hyperdrive.krpc.session

interface SessionContextKeyRegistry {
    val allKeys: List<Session.Context.Key<*>>

    fun getKeyByQualifiedName(keyQualifiedName: String): Session.Context.Key<*>?

    object Empty: SessionContextKeyRegistry {
        override val allKeys: List<Session.Context.Key<*>> = emptyList()

        override fun getKeyByQualifiedName(keyQualifiedName: String): Session.Context.Key<*>? = null
    }
}