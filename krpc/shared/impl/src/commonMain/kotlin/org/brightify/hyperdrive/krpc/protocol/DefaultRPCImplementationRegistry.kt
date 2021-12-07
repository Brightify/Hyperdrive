package org.brightify.hyperdrive.krpc.protocol

import org.brightify.hyperdrive.krpc.ServiceRegistry
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.error.RPCNotFoundError
import org.brightify.hyperdrive.krpc.application.runner.ColdBistreamRunner
import org.brightify.hyperdrive.krpc.application.runner.ColdDownstreamRunner
import org.brightify.hyperdrive.krpc.application.runner.ColdUpstreamRunner
import org.brightify.hyperdrive.krpc.application.PayloadSerializer
import org.brightify.hyperdrive.krpc.application.runner.SingleCallRunner
import kotlin.reflect.KClass

public class DefaultRPCImplementationRegistry(
    private val payloadSerializer: PayloadSerializer,
    private val serviceRegistry: ServiceRegistry,
): RPCImplementationRegistry {
    override fun <T: RPC.Implementation> callImplementation(id: ServiceCallIdentifier, type: KClass<T>): T {
        val runnableCall = serviceRegistry.getCallById(id, RunnableCallDescription::class)
        @Suppress("UNCHECKED_CAST")
        return when (runnableCall) {
            is RunnableCallDescription.Single<*, *> -> SingleCallRunner.Callee(payloadSerializer, runnableCall) as T
            is RunnableCallDescription.ColdUpstream<*, *, *> -> ColdUpstreamRunner.Callee(payloadSerializer, runnableCall) as T
            is RunnableCallDescription.ColdDownstream<*, *> -> ColdDownstreamRunner.Callee(payloadSerializer, runnableCall) as T
            is RunnableCallDescription.ColdBistream<*, *, *> -> ColdBistreamRunner.Callee(payloadSerializer, runnableCall) as T
            null -> throw RPCNotFoundError(id)
        }
    }
}
