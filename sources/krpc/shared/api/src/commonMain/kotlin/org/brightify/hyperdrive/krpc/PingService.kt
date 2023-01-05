package org.brightify.hyperdrive.krpc

import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.util.RPCDataWrapper0
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import org.brightify.hyperdrive.krpc.description.ServiceDescriptor
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer

// Intentionally not marked as @EnableKRPC as we need to implement client and server in separate modules.
public interface PingService {
    public suspend fun ping()

    public class Client(private val transport: RPCTransport): PingService {
        public override suspend fun ping() {
            return transport.singleCall(Descriptor.Call.ping, RPCDataWrapper0())
        }
    }

    public object Descriptor: ServiceDescriptor<PingService> {
        public val serviceIdentifier: String = "builtin:hyperdrive.PingService"

        public object Call {
            public val ping: SingleCallDescription<Unit, Unit> = SingleCallDescription(
                ServiceCallIdentifier(serviceIdentifier, "ping"),
                Unit.serializer(),
                Unit.serializer(),
                RPCErrorSerializer(),
            )
        }

        public override fun describe(service: PingService): ServiceDescription {
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