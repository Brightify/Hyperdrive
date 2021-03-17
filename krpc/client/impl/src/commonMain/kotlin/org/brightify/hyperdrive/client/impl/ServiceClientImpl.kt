package org.brightify.hyperdrive.client.impl

import io.ktor.utils.io.core.*
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
import org.brightify.hyperdrive.client.api.ServiceClient
import org.brightify.hyperdrive.krpc.api.ClientCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdBistreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdDownstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdUpstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.RPCClientConnector
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.api.RPCProtocol
import org.brightify.hyperdrive.krpc.api.impl.AscensionRPCProtocol
import org.brightify.hyperdrive.krpc.api.impl.ProtocolUpgradeService
import org.brightify.hyperdrive.krpc.api.impl.ServiceRegistry


@Serializable
class UnsupportedRPCProtocolError private constructor(override val debugMessage: String): RPCError() {
    override val statusCode = StatusCode.BadRequest

    constructor(supportedProtocols: Set<RPCProtocol.Version>, requestedProtocol: Int): this(
        "Supported protocols: ${supportedProtocols.joinToString { "${it.name}(${it.literal})" } }, required protocol version: $requestedProtocol"
    )
}

class ServiceClientImpl(
    private val transport: RPCClientConnector,
//    private val contextSerializer: ContextSerializer,
    serviceRegistry: ServiceRegistry,
    communicationScope: CoroutineScope,
    outStreamScope: CoroutineScope,
    responseScope: CoroutineScope,
): ServiceClient {
    private val runJob: Job

    private val initialProtocolFactory = AscensionRPCProtocol.Factory(serviceRegistry, outStreamScope, responseScope)
    private val supportedProtocols = listOf<RPCProtocol.Factory>(initialProtocolFactory)
    private val activeProtocol = MutableStateFlow<RPCProtocol?>(null)

    init {
        runJob = communicationScope.launch {
            while (isActive) {
                try {
                    transport.withConnection {
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
                                // TODO: Add logging.
                                // Here the server wants to use a protocol we don't support so we'll just close this connection and give it
                                // an another try.
                                close()
                                delay(1_000)
                            }
                        } else {
                            protocolUpgradeService.confirmProtocolSelected()
                            activeProtocol.value = protocol
                        }
                    }
                } catch (e: ClosedReceiveChannelException) {
                    e.printStackTrace()
                    // Disconnected from server, wait and then try again
                    delay(500)
                } catch (e: EOFException) {
                    e.printStackTrace()
                    // Disconnected from server, wait and then try again
                    delay(500)
                } catch (t: Throwable) {
                    t.printStackTrace()
                    throw t
                }
            }
        }
    }

    private suspend fun <T> withActiveProtocol(block: suspend RPCProtocol.() -> T): T {
        return activeProtocol.filterNotNull().filter { it.isActive }.first().let { block(it) }
    }

    override suspend fun <REQUEST, RESPONSE> singleCall(serviceCall: ClientCallDescriptor<REQUEST, RESPONSE>, request: REQUEST): RESPONSE = withActiveProtocol {
        singleCall(serviceCall, request)
    }

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> clientStream(serviceCall: ColdUpstreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>, request: REQUEST, clientStream: Flow<CLIENT_STREAM>): RESPONSE = withActiveProtocol {
        clientStream(serviceCall, request, clientStream)
    }

    override suspend fun <REQUEST, RESPONSE> serverStream(
        serviceCall: ColdDownstreamCallDescriptor<REQUEST, RESPONSE>,
        request: REQUEST
    ): Flow<RESPONSE> = withActiveProtocol {
        serverStream(serviceCall, request)
    }

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> biStream(
        serviceCall: ColdBistreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>,
        request: REQUEST,
        clientStream: Flow<CLIENT_STREAM>
    ): Flow<RESPONSE> = withActiveProtocol {
        biStream(serviceCall, request, clientStream)
    }

    override suspend fun shutdown() {
        activeProtocol.value?.close()
        runJob.cancelAndJoin()
    }
}
