package org.brightify.hyperdrive.krpc.description

data class ServiceDescription(
    val identifier: String,
    val calls: List<RunnableCallDescription<*>>
)