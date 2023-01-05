package org.brightify.hyperdrive.krpc.session.impl

import org.brightify.hyperdrive.krpc.session.Session
import org.brightify.hyperdrive.krpc.session.SessionContextKeyRegistry

public class DefaultSessionContextKeyRegistry(
    vararg keys: Session.Context.Key<*>,
): SessionContextKeyRegistry {
    override val allKeys: List<Session.Context.Key<*>> = keys.toList()

    private val keyMap = allKeys.associateBy { it.qualifiedName }

    override fun getKeyByQualifiedName(keyQualifiedName: String): Session.Context.Key<*>? {
        return keyMap[keyQualifiedName]
    }

}