package org.brightify.hyperdrive.client.impl

import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.krpc.PingService
import kotlin.Unit
import org.brightify.hyperdrive.client.api.ServiceClient
import org.brightify.hyperdrive.krpc.api.ClientCallDescriptor
import org.brightify.hyperdrive.krpc.api.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

class PingServiceClient(private val serviceClient: ServiceClient) : PingService {
    companion object Ids {
        val ping = ClientCallDescriptor(
            ServiceCallIdentifier("PingService", "ping"),
            Unit.serializer(),
            Unit.serializer(),
            RPCErrorSerializer(),
        )
    }

    override suspend fun ping(): Unit {
        return serviceClient.singleCall(ping, Unit)
    }
}
