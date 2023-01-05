package org.brightify.hyperdrive.krpc.session

public data class OutgoingContextUpdate(
    val updates: Map<Session.Context.Key<Any>, Modification<Any>>,
) {
    public sealed class Modification<VALUE> {
        public class Set<VALUE>(
            public val oldRevision: Int?,
            public val newRevision: Int,
            public val newValue: VALUE,
        ): Modification<VALUE>()
        public class Remove(public val oldRevision: Int): Modification<Any>()
    }
}

