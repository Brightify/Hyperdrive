package org.brightify.hyperdrive.krpc

import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import kotlin.reflect.KClass

interface ServiceRegistry {
    fun <T: RunnableCallDescription<*>> getCallById(id: ServiceCallIdentifier, type: KClass<T>): T?

    object Empty: ServiceRegistry {
        override fun <T: RunnableCallDescription<*>> getCallById(id: ServiceCallIdentifier, type: KClass<T>): T? = null
    }
}

