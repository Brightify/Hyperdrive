package org.brightify.hyperdrive.krpc.server.impl

import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.api.ServiceDescription
import kotlin.reflect.KClass

class DefaultServiceRegistry: ServiceRegistry {
    private val services: MutableMap<String, ServiceDescription> = mutableMapOf()
    private val serviceCalls: MutableMap<String, Map<String, CallDescriptor>> = mutableMapOf()

    override fun register(description: ServiceDescription) {
        services[description.identifier] = description

        serviceCalls[description.identifier] = description.calls.map {
            it.identifier.callId to it
        }.toMap()
    }

    override fun <T: CallDescriptor> getCallById(id: ServiceCallIdentifier, type: KClass<T>): T? {
        val knownCall = serviceCalls[id.serviceId]?.get(id.callId) ?: return null
        return if (type.isInstance(knownCall)) {
            knownCall as T
        } else {
            null
        }
    }
}