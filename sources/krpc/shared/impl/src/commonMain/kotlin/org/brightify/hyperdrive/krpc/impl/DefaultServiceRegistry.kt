package org.brightify.hyperdrive.krpc.impl

import org.brightify.hyperdrive.krpc.MutableServiceRegistry
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import kotlin.reflect.KClass

public class DefaultServiceRegistry: MutableServiceRegistry {
    private val services: MutableMap<String, ServiceDescription> = mutableMapOf()
    private val serviceCalls: MutableMap<String, Map<String, RunnableCallDescription<*>>> = mutableMapOf()

    override fun register(description: ServiceDescription) {
        services[description.identifier] = description

        serviceCalls[description.identifier] = description.calls.map {
            it.identifier.callId to it
        }.toMap()
    }

    override fun <T: RunnableCallDescription<*>> getCallById(id: ServiceCallIdentifier, type: KClass<T>): T? {
        val knownCall = serviceCalls[id.serviceId]?.get(id.callId) ?: return null
        return if (type.isInstance(knownCall)) {
            @Suppress("UNCHECKED_CAST")
            knownCall as T
        } else {
            null
        }
    }
}