package org.brightify.hyperdrive.krpc

import org.brightify.hyperdrive.krpc.description.ServiceDescription

interface MutableServiceRegistry: ServiceRegistry {
    fun register(description: ServiceDescription)
}