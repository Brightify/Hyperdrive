package org.brightify.hyperdrive.krpc.extension.session

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.SerializedPayload

@Serializable
public class ContextItemDto(
    public val revision: Int,
    public val value: SerializedPayload,
)

