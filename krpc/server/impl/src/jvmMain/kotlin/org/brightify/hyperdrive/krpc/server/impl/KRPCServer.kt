package org.brightify.hyperdrive.krpc.server.impl

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import org.brightify.hyperdrive.krpc.api.RPCConnection
import org.brightify.hyperdrive.krpc.api.RPCProtocol
import org.brightify.hyperdrive.krpc.api.impl.AscensionRPCProtocol
import org.brightify.hyperdrive.krpc.api.impl.DefaultServiceRegistry
import org.brightify.hyperdrive.krpc.api.impl.ProtocolUpgradeService
import org.brightify.hyperdrive.krpc.api.impl.ServiceRegistry

class KRPCServer(
    val serviceRegistry: ServiceRegistry,
//    val sessionManager: SecurityManager,
    val outStreamScope: CoroutineScope,
    val responseScope: CoroutineScope,
) {
    private val initialProtocolFactory: (ServiceRegistry) -> AscensionRPCProtocol.Factory = {
        AscensionRPCProtocol.Factory(it, outStreamScope, responseScope)
    }

    private val defaultProtocolFactory = initialProtocolFactory(serviceRegistry)
    private val supportedProtocols = listOf<RPCProtocol.Factory>(
        defaultProtocolFactory,
    ).map { it.version to it }.toMap()
    private val supportedProtocolVersions = supportedProtocols.keys

    suspend fun handleNewConnection(connection: RPCConnection) {
        val upgradedProtocolFactory = CompletableDeferred<RPCProtocol.Factory>()
        val protocolUpgradeConfirmed = CompletableDeferred<Unit>()

        val protocolUpgradeService = object: ProtocolUpgradeService {
            override suspend fun upgradeIfPossible(supportedProtocols: List<RPCProtocol.Version>): RPCProtocol.Version {
                val foundSupportedProtocolVersion = supportedProtocols.firstOrNull(supportedProtocolVersions::contains)
                val protocolToUse = foundSupportedProtocolVersion?.let {
                    this@KRPCServer.supportedProtocols[it]
                } ?: defaultProtocolFactory
                upgradedProtocolFactory.complete(protocolToUse)
                return protocolToUse.version
            }

            override suspend fun confirmProtocolSelected() {
                protocolUpgradeConfirmed.complete(Unit)
            }
        }

        val protocolUpgradeServiceRegistry = DefaultServiceRegistry()
        protocolUpgradeServiceRegistry.register(
            ProtocolUpgradeService.Descriptor.describe(protocolUpgradeService)
        )

        val initialProtocol = initialProtocolFactory(protocolUpgradeServiceRegistry).create(connection)

        val selectedProtocolFactory = upgradedProtocolFactory.await()
        protocolUpgradeConfirmed.await()
        initialProtocol.detach()

        val selectedProtocol = selectedProtocolFactory.create(connection)

        selectedProtocol.join()
    }
}