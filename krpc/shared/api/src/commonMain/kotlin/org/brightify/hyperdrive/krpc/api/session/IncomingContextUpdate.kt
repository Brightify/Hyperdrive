package org.brightify.hyperdrive.krpc.api.session

data class IncomingContextUpdate(
    val updates: Map<Session.Context.Key<*>, Modification>,
) {
    sealed class Modification {
        class Set(val oldRevision: Int?, val newRevision: Int, val newValue: Any): Modification()
        class Remove(val oldRevision: Int): Modification()
    }
}
