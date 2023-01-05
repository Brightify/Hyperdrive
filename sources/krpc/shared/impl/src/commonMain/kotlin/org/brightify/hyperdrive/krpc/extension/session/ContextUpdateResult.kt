package org.brightify.hyperdrive.krpc.extension.session

import kotlinx.serialization.Serializable

@Serializable
public sealed class ContextUpdateResult {
    @Serializable
    public object Accepted: ContextUpdateResult()
    @Serializable
    public class Rejected(
        public val rejectedModifications: Map<KeyDto, Reason> = emptyMap(),
    ): ContextUpdateResult() {
        @Serializable
        public sealed class Reason {
            @Serializable
            public object Removed: Reason()
            @Serializable
            public class Updated(public val newItem: ContextItemDto): Reason()
        }
    }
}