package org.brightify.hyperdrive.krpc

import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import kotlin.reflect.KClass

public interface ServiceRegistry {
    public fun <T: RunnableCallDescription<*>> getCallById(id: ServiceCallIdentifier, type: KClass<T>): T?

    public object Empty: ServiceRegistry {
        override fun <T: RunnableCallDescription<*>> getCallById(id: ServiceCallIdentifier, type: KClass<T>): T? = null
    }
}

