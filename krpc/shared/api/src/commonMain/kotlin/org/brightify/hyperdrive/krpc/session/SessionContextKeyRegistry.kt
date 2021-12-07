package org.brightify.hyperdrive.krpc.session

public interface SessionContextKeyRegistry {
    public val allKeys: List<Session.Context.Key<*>>

    public fun getKeyByQualifiedName(keyQualifiedName: String): Session.Context.Key<*>?

    public object Empty: SessionContextKeyRegistry {
        override val allKeys: List<Session.Context.Key<*>> = emptyList()

        override fun getKeyByQualifiedName(keyQualifiedName: String): Session.Context.Key<*>? = null
    }
}