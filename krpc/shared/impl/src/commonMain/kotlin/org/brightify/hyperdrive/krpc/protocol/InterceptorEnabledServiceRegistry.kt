package org.brightify.hyperdrive.krpc.protocol

import org.brightify.hyperdrive.krpc.ServiceRegistry
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import kotlin.reflect.KClass

class InterceptorEnabledServiceRegistry(
    private val serviceRegistry: ServiceRegistry,
    private val interceptor: RPCIncomingInterceptor,
): ServiceRegistry {
    override fun <T: RunnableCallDescription<*>> getCallById(id: ServiceCallIdentifier, type: KClass<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return when (val originalDescription = serviceRegistry.getCallById(id, type)) {
            is RunnableCallDescription.Single<*, *> -> originalDescription.interceptedWith(interceptor) as T
            is RunnableCallDescription.ColdUpstream<*, *, *> -> originalDescription.interceptedWith(interceptor) as T
            is RunnableCallDescription.ColdDownstream<*, *> -> originalDescription.interceptedWith(interceptor) as T
            is RunnableCallDescription.ColdBistream<*, *, *> -> originalDescription.interceptedWith(interceptor) as T
            else -> originalDescription
        }
    }
}