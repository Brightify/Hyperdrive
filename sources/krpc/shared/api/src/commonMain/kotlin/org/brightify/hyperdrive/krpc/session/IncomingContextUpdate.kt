package org.brightify.hyperdrive.krpc.session

public data class IncomingContextUpdate(
    val updates: Map<Session.Context.Key<*>, Modification>,
) {
    public sealed class Modification {
        public class Set(public val oldRevision: Int?, public val newRevision: Int, public val newValue: Any): Modification()
        public class Remove(public val oldRevision: Int): Modification()
    }
}
