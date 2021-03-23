package org.brightify.hyperdrive.krpc.client.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.ServiceRegistry
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.description.ColdBistreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdDownstreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription
import org.brightify.hyperdrive.krpc.api.InternalRPCError
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.protocol.ProtocolUpgradeService
import org.brightify.hyperdrive.krpc.client.RPCClientConnector
import org.brightify.hyperdrive.krpc.protocol.RPCProtocol
import org.brightify.hyperdrive.krpc.protocol.ascension.AscensionRPCProtocol

@Serializable
class UnsupportedRPCProtocolError(
    val supportedProtocols: Set<RPCProtocol.Version>,
    val requestedProtocol: Int
): InternalRPCError(RPCError.StatusCode.BadRequest, "Supported protocols: ${supportedProtocols.joinToString { "${it.name}(${it.literal})" } }, required protocol version: $requestedProtocol")

class ServiceClient(
    private val connector: RPCClientConnector,
//    private val contextSerializer: ContextSerializer,
    serviceRegistry: ServiceRegistry,
    communicationScope: CoroutineScope,
): RPCTransport {
    private companion object {
        val logger = Logger<ServiceClient>()
    }

    private val runJob: Job

    private val initialProtocolFactory = AscensionRPCProtocol.Factory(serviceRegistry)
    private val supportedProtocols = listOf<RPCProtocol.Factory>(initialProtocolFactory)
    private val activeProtocol = MutableStateFlow<RPCProtocol?>(null)

    init {
        runJob = communicationScope.launch {
            while (isActive) {
                try {
                    connector.withConnection {
                        val protocol = initialProtocolFactory.create(this)
                        val protocolUpgradeService = ProtocolUpgradeService.Client(protocol)
                        val selectedProtocolVersion = protocolUpgradeService.upgradeIfPossible(supportedProtocols.map { it.version })
                        // The server can select a new protocol to be used, or keep the default one.
                        if (selectedProtocolVersion != protocol.version) {
                            val selectedProtocolFactory = supportedProtocols.firstOrNull { it.version == selectedProtocolVersion }
                            if (selectedProtocolFactory != null) {
                                protocolUpgradeService.confirmProtocolSelected()
                                protocol.detach()
                                activeProtocol.value = selectedProtocolFactory.create(this)
                            } else {
                                // Here the server wants to use a protocol we don't support so we'll just close this connection and give it
                                // an another try.
                                logger.error { "Client doesn't support requested protocol, closing." }
                                close()
                                delay(1_000)
                            }
                        } else {
                            protocolUpgradeService.confirmProtocolSelected()
                            activeProtocol.value = protocol
                        }

                        activeProtocol.value?.join()
                    }
                } catch (t: Throwable) {
                    if (t is ClosedReceiveChannelException || connector.isConnectionCloseException(t)) {
                        logger.warning(t) { "Client connection disconnected. Trying to reconnect soon." }
                        // Disconnected from server, wait and then try again
                        delay(500)
                    } else {
                        logger.error(t) { "Unexpected error! Rethrowing." }
                        throw t
                    }
                }
            }
        }
    }

    private suspend fun <T> withActiveProtocol(block: suspend RPCProtocol.() -> T): T {
        return activeProtocol.filterNotNull().filter { it.isActive }.first().let { block(it) }
    }

    override suspend fun <REQUEST, RESPONSE> singleCall(serviceCall: SingleCallDescription<REQUEST, RESPONSE>, request: REQUEST): RESPONSE = withActiveProtocol {
        singleCall(serviceCall, request)
    }

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> clientStream(serviceCall: ColdUpstreamCallDescription<REQUEST, CLIENT_STREAM, RESPONSE>, request: REQUEST, clientStream: Flow<CLIENT_STREAM>): RESPONSE = withActiveProtocol {
        clientStream(serviceCall, request, clientStream)
    }

    override suspend fun <REQUEST, RESPONSE> serverStream(
        serviceCall: ColdDownstreamCallDescription<REQUEST, RESPONSE>,
        request: REQUEST
    ): Flow<RESPONSE> = withActiveProtocol {
        serverStream(serviceCall, request)
    }

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> biStream(
        serviceCall: ColdBistreamCallDescription<REQUEST, CLIENT_STREAM, RESPONSE>,
        request: REQUEST,
        clientStream: Flow<CLIENT_STREAM>
    ): Flow<RESPONSE> = withActiveProtocol {
        biStream(serviceCall, request, clientStream)
    }

    override suspend fun close() {
        activeProtocol.value?.let {
            if (it.isActive) {
                it.close()
            }
        }
        if (runJob.isActive) {
            runJob.cancelAndJoin()
        }
    }
}