package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.Serializable

@Serializable
data class Context(
    val metadata: Map<String, String>
)