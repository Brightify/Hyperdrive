package org.brightify.hyperdrive.krpc.description

public data class ServiceDescription(
    val identifier: String,
    val calls: List<RunnableCallDescription<*>>
)