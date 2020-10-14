package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.Serializable

@Serializable
data class ServiceCallIdentifier(
    val serviceId: String,
    val callId: String
)