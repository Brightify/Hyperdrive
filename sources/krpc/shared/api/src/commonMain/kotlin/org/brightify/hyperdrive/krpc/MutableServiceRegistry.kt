package org.brightify.hyperdrive.krpc

import org.brightify.hyperdrive.krpc.description.ServiceDescription

public interface MutableServiceRegistry: ServiceRegistry {
    public fun register(description: ServiceDescription)
}