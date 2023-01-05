package org.brightify.hyperdrive.krpc.extension.session

import kotlinx.serialization.KSerializer
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.session.Session

public data class UnsupportedKey(override val qualifiedName: String): Session.Context.Key<SerializedPayload> {
    override val serializer: KSerializer<SerializedPayload> = SerializedPayload.serializer()
}