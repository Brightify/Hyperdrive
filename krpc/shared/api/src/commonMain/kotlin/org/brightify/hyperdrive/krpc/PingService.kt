package org.brightify.hyperdrive.krpc

import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.krpc.api.ClientCallDescriptor
import org.brightify.hyperdrive.krpc.api.RPCDataWrapper0
import org.brightify.hyperdrive.krpc.api.RPCTransport
import org.brightify.hyperdrive.krpc.api.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.api.ServiceDescription
import org.brightify.hyperdrive.krpc.api.ServiceDescriptor
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

// Intentionally not marked as @EnableKRPC as we need to implement client and server in separate modules.
interface PingService {
    suspend fun ping()

    class Client(private val transport: RPCTransport): PingService {
        override suspend fun ping() {
            return transport.singleCall(Descriptor.Call.ping, RPCDataWrapper0())
        }
    }

    object Descriptor: ServiceDescriptor<PingService> {
        val serviceIdentifier = "hyperdrive.PingService"

        object Call {
            val ping = ClientCallDescriptor(
                ServiceCallIdentifier(serviceIdentifier, "ping"),
                Unit.serializer(),
                Unit.serializer(),
                RPCErrorSerializer(),
            )
        }

        override fun describe(service: PingService): ServiceDescription {
            return ServiceDescription(
                serviceIdentifier,
                listOf(
                    Call.ping.calling {
                        service.ping()
                    }
                )
            )
        }
    }

}