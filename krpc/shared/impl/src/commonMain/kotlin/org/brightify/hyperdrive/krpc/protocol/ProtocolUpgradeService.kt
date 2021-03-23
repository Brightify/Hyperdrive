package org.brightify.hyperdrive.krpc.protocol

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import org.brightify.hyperdrive.krpc.description.ServiceDescriptor
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer

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

        val upgradeIfPossible = SingleCallDescription(
            ServiceCallIdentifier(serviceId, ProtocolUpgradeService::upgradeIfPossible.name),
            ListSerializer(RPCProtocol.Version.serializer()),
            RPCProtocol.Version.serializer(),
            RPCErrorSerializer(),
        )

        val confirmProtocolSelected = SingleCallDescription(
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