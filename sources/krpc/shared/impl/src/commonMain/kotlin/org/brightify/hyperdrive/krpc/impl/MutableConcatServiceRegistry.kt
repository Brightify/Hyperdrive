package org.brightify.hyperdrive.krpc.impl

import org.brightify.hyperdrive.krpc.ServiceRegistry
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import kotlin.reflect.KClass

/**
 * Service registry that searches a call in multiple registries passed into its constructor. The priority is ascending, so the first non-null
 * call returned from a registry will be used.
 *
 * NOTE: No two calls should ever have the same ID. This is currently not being enforced, but will be in a later version.
 * TODO: Enforce no two calls in registries have the same ID.
 */
class MutableConcatServiceRegistry(
    ascendingRegistries: List<ServiceRegistry> = emptyList(),
): ServiceRegistry {
    constructor(vararg ascendingRegistries: ServiceRegistry): this(ascendingRegistries.toList())

    private val ascendingRegistries = ascendingRegistries.toMutableList()

    fun prepend(registry: ServiceRegistry) {
        ascendingRegistries.add(0, registry)
    }

    fun append(registry: ServiceRegistry) {
        ascendingRegistries.add(registry)
    }

    override fun <T: RunnableCallDescription<*>> getCallById(id: ServiceCallIdentifier, type: KClass<T>): T? {
        for (registry in ascendingRegistries) {
            return registry.getCallById(id, type) ?: continue
        }
        return null
    }
}