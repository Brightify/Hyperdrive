package org.brightify.hyperdrive.krpc.description

import kotlinx.serialization.Serializable

@Serializable
public data class ServiceCallIdentifier(
    val serviceId: String,
    val callId: String
) {
    override fun toString(): String {
        return "$serviceId.$callId"
    }
}