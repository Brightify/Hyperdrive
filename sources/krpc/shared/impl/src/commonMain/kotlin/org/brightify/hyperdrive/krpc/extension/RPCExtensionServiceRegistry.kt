package org.brightify.hyperdrive.krpc.extension

import org.brightify.hyperdrive.krpc.ServiceRegistry
import org.brightify.hyperdrive.krpc.application.RPCNodeExtension
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.impl.DefaultServiceRegistry
import kotlin.reflect.KClass

class RPCExtensionServiceRegistry(extensions: List<RPCNodeExtension>): ServiceRegistry {
    private val registry = DefaultServiceRegistry()

    init {
        for (extension in extensions) {
            for (service in extension.providedServices) {
                registry.register(service)
            }
        }
    }

    override fun <T: RunnableCallDescription<*>> getCallById(id: ServiceCallIdentifier, type: KClass<T>): T? {
        return registry.getCallById(id, type)
    }
}