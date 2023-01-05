package org.brightify.hyperdrive.krpc.protocol

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import org.brightify.hyperdrive.krpc.description.ServiceDescriptor
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer

// TODO: Currently unused, let's see if can be removed.
public interface ProtocolUpgradeService {
    public suspend fun upgradeIfPossible(supportedProtocols: List<RPCProtocol.Version>): RPCProtocol.Version

    public suspend fun confirmProtocolSelected()

    public class Client(private val transport: RPCTransport): ProtocolUpgradeService {
        override suspend fun upgradeIfPossible(supportedProtocols: List<RPCProtocol.Version>): RPCProtocol.Version {
            return transport.singleCall(Descriptor.upgradeIfPossible, supportedProtocols)
        }

        override suspend fun confirmProtocolSelected() {
            return transport.singleCall(Descriptor.confirmProtocolSelected, Unit)
        }
    }

    public object Descriptor: ServiceDescriptor<ProtocolUpgradeService> {
        public const val serviceId: String = "hyperdrive.ProtocolUpgradeService"

        public val upgradeIfPossible: SingleCallDescription<List<RPCProtocol.Version>, RPCProtocol.Version> = SingleCallDescription(
            ServiceCallIdentifier(serviceId, ProtocolUpgradeService::upgradeIfPossible.name),
            ListSerializer(RPCProtocol.Version.serializer()),
            RPCProtocol.Version.serializer(),
            RPCErrorSerializer(),
        )

        public val confirmProtocolSelected: SingleCallDescription<Unit, Unit> = SingleCallDescription(
            ServiceCallIdentifier(serviceId, ProtocolUpgradeService::confirmProtocolSelected.name),
            Unit.serializer(),
            Unit.serializer(),
            RPCErrorSerializer(),
        )

        override fun describe(service: ProtocolUpgradeService): ServiceDescription {
            return ServiceDescription(serviceId, listOf(
                upgradeIfPossible.calling { service.upgradeIfPossible(it) },
                confirmProtocolSelected.calling { service.confirmProtocolSelected() },
            ))
        }
    }
}