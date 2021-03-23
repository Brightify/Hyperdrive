package org.brightify.hyperdrive.krpc.session

data class OutgoingContextUpdate(
    val updates: Map<Session.Context.Key<Any>, Modification<Any>>,
) {
    sealed class Modification<VALUE> {
        class Set<VALUE>(val oldRevision: Int?, val newRevision: Int, val newValue: VALUE): Modification<VALUE>()
        class Remove(val oldRevision: Int): Modification<Any>()
    }
}

