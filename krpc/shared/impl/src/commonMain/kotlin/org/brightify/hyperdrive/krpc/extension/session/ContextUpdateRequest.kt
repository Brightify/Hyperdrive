package org.brightify.hyperdrive.krpc.extension.session

import kotlinx.serialization.Serializable

@Serializable
public class ContextUpdateRequest(
    public val modifications: Map<KeyDto, Modification> = emptyMap(),
) {
    @Serializable
    public sealed class Modification {
        public abstract val oldRevisionOrNull: Int?

        @Serializable
        public class Required(public val oldRevision: Int? = null): Modification() {
            override val oldRevisionOrNull: Int?
                get() = oldRevision
        }

        @Serializable
        public class Set(public val oldRevision: Int? = null, public val newItem: ContextItemDto): Modification() {
            override val oldRevisionOrNull: Int?
                get() = oldRevision
        }
        @Serializable
        public class Remove(public val oldRevision: Int): Modification() {
            override val oldRevisionOrNull: Int
                get() = oldRevision
        }
    }
}