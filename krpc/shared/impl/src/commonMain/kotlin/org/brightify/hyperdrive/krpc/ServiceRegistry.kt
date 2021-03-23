package org.brightify.hyperdrive.krpc

import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import kotlin.reflect.KClass

interface ServiceRegistry {
    fun register(description: ServiceDescription)

    fun <T: RunnableCallDescription<*>> getCallById(id: ServiceCallIdentifier, type: KClass<T>): T?
}