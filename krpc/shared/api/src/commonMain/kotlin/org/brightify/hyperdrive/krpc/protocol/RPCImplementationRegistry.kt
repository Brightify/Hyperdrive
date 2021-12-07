package org.brightify.hyperdrive.krpc.protocol

import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import kotlin.reflect.KClass

public interface RPCImplementationRegistry {
    public fun <T: RPC.Implementation> callImplementation(id: ServiceCallIdentifier, type: KClass<T>): T
}

public inline fun <reified T: RPC.Implementation> RPCImplementationRegistry.callImplementation(id: ServiceCallIdentifier): T {
    return callImplementation(id, T::class)
}
