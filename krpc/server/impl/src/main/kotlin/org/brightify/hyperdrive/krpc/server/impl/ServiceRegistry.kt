package org.brightify.hyperdrive.krpc.server.impl

import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.api.ServiceDescription
import kotlin.reflect.KClass

interface ServiceRegistry {
    fun register(description: ServiceDescription)

    fun <T: CallDescriptor> getCallById(id: ServiceCallIdentifier, type: KClass<T>): T?
}