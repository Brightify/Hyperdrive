package org.brightify.hyperdrive.krpc.api

data class ServiceDescription(
    val identifier: String,
    val calls: List<CallDescriptor<*>>
)