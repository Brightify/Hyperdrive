package org.brightify.hyperdrive.krpc.api.impl

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.krpc.api.ClientCallDescriptor
import org.brightify.hyperdrive.krpc.protocol.RPCProtocol
import org.brightify.hyperdrive.krpc.api.RPCTransport
import org.brightify.hyperdrive.krpc.api.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.api.ServiceDescription
import org.brightify.hyperdrive.krpc.api.ServiceDescriptor
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

interface ProtocolUpgradeService {
    suspend fun upgradeIfPossible(supportedProtocols: List<RPCProtocol.Version>): RPCProtocol.Version

    suspend fun confirmProtocolSelected()

    class Client(private val transport: RPCTransport): ProtocolUpgradeService {
        override suspend fun upgradeIfPossible(supportedProtocols: List<RPCProtocol.Version>): RPCProtocol.Version {
            return transport.singleCall(Descriptor.upgradeIfPossible, supportedProtocols)
        }

        override suspend fun confirmProtocolSelected() {
            return transport.singleCall(Descriptor.confirmProtocolSelected, Unit)
        }
    }

    object Descriptor: ServiceDescriptor<ProtocolUpgradeService> {
        val serviceId = "hyperdrive.ProtocolUpgradeService"

        val upgradeIfPossible = ClientCallDescriptor(
            ServiceCallIdentifier(serviceId, ProtocolUpgradeService::upgradeIfPossible.name),
            ListSerializer(RPCProtocol.Version.serializer()),
            RPCProtocol.Version.serializer(),
            RPCErrorSerializer(),
        )

        val confirmProtocolSelected = ClientCallDescriptor(
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