package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.SerializedFrame
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.protocol.RPCProtocol
import org.brightify.hyperdrive.krpc.util.RPCReference
import org.brightify.hyperdrive.krpc.frame.AscensionRPCFrame
import org.brightify.hyperdrive.krpc.frame.HandshakeRPCFrame
import org.brightify.hyperdrive.krpc.protocol.RPC
import org.brightify.hyperdrive.krpc.protocol.RPCImplementationRegistry
import org.brightify.hyperdrive.krpc.protocol.callImplementation
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer
import org.brightify.hyperdrive.utils.Do

interface RPCHandshakePerformer {
    sealed class HandshakeResult {
        class Success(
            val selectedFrameSerializer: TransportFrameSerializer,
            val selectedProtocolFactory: RPCProtocol.Factory,
        ): HandshakeResult()
        class Failed(val message: String): HandshakeResult()
    }

    suspend fun performHandshake(connection: RPCConnection): HandshakeResult

    class NoHandshake(
        val selectedFrameSerializer: TransportFrameSerializer,
        val selectedProtocolFactory: RPCProtocol.Factory,
    ): RPCHandshakePerformer {
        override suspend fun performHandshake(connection: RPCConnection): HandshakeResult {
            return HandshakeResult.Success(selectedFrameSerializer, selectedProtocolFactory)
        }
    }
}

class DefaultRPCHandshakePerformer(
    private val frameSerializerFactory: TransportFrameSerializer.Factory,
    private val behavior: Behavior,
): RPCHandshakePerformer {
    private companion object {
        const val handshakeTimeoutInMillis = 60 * 1000L
        const val totalHandshakeTimeoutInMillis = handshakeTimeoutInMillis * 5
        val logger = Logger<RPCHandshakePerformer>()
        const val handshakeVersion: Byte = Byte.MIN_VALUE
    }
    private val supportedProtocolsByPriority = listOf<RPCProtocol.Factory>(AscensionRPCProtocol.Factory())
    private val supportedProtocolVersionsByPriority = supportedProtocolsByPriority.map { it.version }
    private val supportedProtocolsMap = supportedProtocolsByPriority.map { it.version to it }.toMap()


    private class HandshakeFailedException(message: String): Throwable(message)

    override suspend fun performHandshake(connection: RPCConnection): RPCHandshakePerformer.HandshakeResult = withTimeout(totalHandshakeTimeoutInMillis) {
        try {
            assertHandshakeVersion(connection)
            val selectedSerializationFormat = selectSerializationFormat(connection)
            val selectedFrameSerializer = frameSerializerFactory.create(selectedSerializationFormat)
            val selectedProtocolFactory = selectProtocol(connection, selectedFrameSerializer)
            RPCHandshakePerformer.HandshakeResult.Success(selectedFrameSerializer, selectedProtocolFactory)
        } catch (e: SerializationException) {
            logger.error(e) { "Couldn't complete handshake because of a serialization issue." }
            RPCHandshakePerformer.HandshakeResult.Failed(e.message ?: "Unknown serialization failure during handshake.")
        } catch(e: HandshakeFailedException) {
            RPCHandshakePerformer.HandshakeResult.Failed(e.message ?: "Unknown handshake failure.")
        }
    }

    private suspend fun assertHandshakeVersion(connection: RPCConnection) {
        logger.trace { "[$behavior] Will assert handshake version." }
        Do exhaustive when (behavior) {
            Behavior.Server -> {
                connection.sendByte(handshakeVersion)
            }
            Behavior.Client -> {
                val receivedHandshakeVersion = connection.receiveByte()
                if (receivedHandshakeVersion == handshakeVersion) {
                    logger.info { "[$behavior] Handshake version confirmed using binary." }
                } else {
                    logger.error { "[$behavior] Couldn't confirm handshake version using binary. Expected $handshakeVersion, received ${receivedHandshakeVersion}." }
                    throw HandshakeFailedException("Couldn't confirm handshake version.")
                }
            }
        }
    }

    private suspend fun selectSerializationFormat(connection: RPCConnection): SerializationFormat {
        logger.trace { "[$behavior] Will select serialization format." }
        return when (behavior) {
            Behavior.Server -> {
                connection.sendAsByteArray(frameSerializerFactory.supportedSerializationFormats.map { it.identifier })
                val selectedSerializationFormatIdentifier = connection.receiveByte()
                val selectedSerializationFormat = SerializationFormat(selectedSerializationFormatIdentifier)
                if (selectedSerializationFormat != null) {
                    if (frameSerializerFactory.supportedSerializationFormats.contains(selectedSerializationFormat)) {
                        selectedSerializationFormat
                    } else {
                        logger.error { "[$behavior] Couldn't select serialization format. Other party requested format $selectedSerializationFormat which isn't supported." }
                        throw HandshakeFailedException("Unsupported serialization format $selectedSerializationFormat")
                    }
                } else {
                    logger.error { "[$behavior] Couldn't select serialization format. Other party requested format with identifier $selectedSerializationFormatIdentifier which is unknown." }
                    throw HandshakeFailedException("Unknown serialization format identifier $selectedSerializationFormatIdentifier")
                }
            }
            Behavior.Client -> {
                val remoteSupportedFormatIdentifiers = connection.receiveByteArray()
                var selectedFormat: SerializationFormat? = null
                val localSupportedFormats = frameSerializerFactory.supportedSerializationFormats.map { it.identifier to it }.toMap()
                for (identifier in remoteSupportedFormatIdentifiers) {
                    val format = localSupportedFormats[identifier] ?: continue
                    selectedFormat = format
                    break
                }
                if (selectedFormat != null) {
                    connection.sendByte(selectedFormat.identifier)
                    selectedFormat
                } else {
                    val message = "[$behavior] Couldn't select serialization format. Other party offered formats with identifiers ${remoteSupportedFormatIdentifiers.map {
                        "$it (${localSupportedFormats[it]?.readableIdentifier ?: "N/A"})"
                    }.joinToString(", ")}, none of which is supported."
                    logger.error { message }
                    throw HandshakeFailedException(message)
                }
            }
        }
    }

    private suspend fun selectProtocol(connection: RPCConnection, serializer: TransportFrameSerializer): RPCProtocol.Factory {
        logger.trace { "[$behavior] Will select protocol." }
        return when (behavior) {
            Behavior.Server -> {
                connection.send(
                    serializer.serialize(
                        HandshakeRPCFrame.ProtocolSelection.Request.serializer(),
                        HandshakeRPCFrame.ProtocolSelection.Request(supportedProtocolVersionsByPriority),
                    )
                )
                val response = withTimeout(handshakeTimeoutInMillis) {
                    serializer.deserialize(HandshakeRPCFrame.ProtocolSelection.Response.serializer(), connection.receive())
                }
                when (response) {
                    is HandshakeRPCFrame.ProtocolSelection.Response.Success -> {
                        val selectedProtocol = supportedProtocolsMap[response.selectedProtocolVersion]
                        if (selectedProtocol == null) {
                            logger.error { "[$behavior] Couldn't select protocol. Other party selected protocol ${response.selectedProtocolVersion} which isn't supported." }
                            connection.send(
                                serializer.serialize(
                                    HandshakeRPCFrame.Complete.serializer(),
                                    HandshakeRPCFrame.Complete.Error("An unsupported protocol version selected, closing."),
                                )
                            )
                            throw HandshakeFailedException("Couldn't select protocol.")
                        } else {
                            connection.send(
                                serializer.serialize(
                                    HandshakeRPCFrame.Complete.serializer(),
                                    HandshakeRPCFrame.Complete.Success,
                                )
                            )
                            selectedProtocol
                        }
                    }
                    is HandshakeRPCFrame.ProtocolSelection.Response.Error -> {
                        logger.error { "[$behavior] Couldn't select protocol. Message: ${response.message}" }
                        throw HandshakeFailedException(response.message)
                    }
                }

            }
            Behavior.Client -> {
                val request = withTimeout(handshakeTimeoutInMillis) {
                    serializer.deserialize(HandshakeRPCFrame.ProtocolSelection.Request.serializer(), connection.receive())
                }
                val selectedProtocolVersion = request.supportedProtocolVersions.firstOrNull { supportedProtocolsMap.keys.contains(it) }
                val selectedProtocol = selectedProtocolVersion?.let(supportedProtocolsMap::get)
                if (selectedProtocol == null) {
                    logger.error { "[$behavior] Couldn't select protocol. Other party requested protocols ${request.supportedProtocolVersions}, none of which we support." }
                    connection.send(
                        serializer.serialize(
                            HandshakeRPCFrame.ProtocolSelection.Response.serializer(),
                            HandshakeRPCFrame.ProtocolSelection.Response.Error("None of the requested protocols are supported, closing."),
                        )
                    )
                    throw HandshakeFailedException("Couldn't select protocol.")
                }

                connection.send(
                    serializer.serialize(
                        HandshakeRPCFrame.ProtocolSelection.Response.serializer(),
                        HandshakeRPCFrame.ProtocolSelection.Response.Success(selectedProtocol.version),
                    )
                )

                val completed = withTimeout(handshakeTimeoutInMillis) {
                    serializer.deserialize(HandshakeRPCFrame.Complete.serializer(), connection.receive())
                }

                when (completed) {
                    HandshakeRPCFrame.Complete.Success -> selectedProtocol
                    is HandshakeRPCFrame.Complete.Error -> {
                        logger.error { "[$behavior] Couldn't complete handshake. Message: ${completed.message}" }
                        throw HandshakeFailedException(completed.message)
                    }
                }
            }
        }
    }

    enum class Behavior {
        Server,
        Client,
    }

    private suspend fun RPCConnection.sendAsByteArray(collection: Collection<Byte>) {
        send(SerializedFrame.Binary(collection.toByteArray()))
    }

    private suspend fun RPCConnection.receiveByteArray(): List<Byte> {
        return when (val frame = receive()) {
            is SerializedFrame.Binary -> {
                frame.binary.toList()
            }
            is SerializedFrame.Text -> {
                frame.text.split(",").mapIndexed { index, part ->
                    part.toByteOrNull() ?: throw HandshakeFailedException("Unexpected data received as byte array. Expected a single byte, got <$part> (index $index of ${frame.text}).")
                }
            }
        }
    }

    private suspend fun RPCConnection.sendByte(byte: Byte) {
        send(SerializedFrame.Binary(byteArrayOf(byte)))
    }

    private suspend fun RPCConnection.receiveByte(): Byte {
        return when (val frame = receive()) {
            is SerializedFrame.Binary -> {
                frame.binary.singleOrNull() ?: throw HandshakeFailedException("Unexpected data received. Expected a single byte, got <${frame.binary}>.")
            }
            is SerializedFrame.Text -> {
                frame.text.toByteOrNull() ?: throw HandshakeFailedException("Unexpected data received. Expected a single byte, got <${frame.text}>.")
            }
        }
    }
}

class AscensionRPCProtocol(
    private val connection: RPCConnection,
    private val frameSerializer: TransportFrameSerializer,
    private val implementationRegistry: RPCImplementationRegistry,
): RPCProtocol, CoroutineScope by connection + SupervisorJob(connection.coroutineContext.job) {
    private companion object {
        val logger = Logger<AscensionRPCProtocol>()
    }

    override val version = RPCProtocol.Version.Ascension

    private val pendingCallees = mutableMapOf<RPCReference, PendingRPC.Callee<*, *>>()
    private val pendingCallers = mutableMapOf<RPCReference, PendingRPC.Caller<*, *>>()

    // TODO: Replace with AtomicInt
    private var callReferenceCounter: RPCReference = RPCReference(UInt.MIN_VALUE)

    override suspend fun run() = withContext(coroutineContext) {
        logger.trace { "Receiving started" }

        try {
            while (isActive) {
                logger.trace { "Will receive" }
                val serializedFrame = connection.receive()
                logger.trace { "Did receive serialized $serializedFrame" }
                val frame = try {
                    frameSerializer.deserialize(AscensionRPCFrame.serializer(), serializedFrame)
                } catch (e: SerializationException) {
                    logger.error(e) { "Failed to deserialize frame $serializedFrame. Ignoring it." }
                    continue
                }
                logger.trace { "Did receive frame $frame" }

                try {
                    Do exhaustive when (frame) {
                        is AscensionRPCFrame.UnknownReferenceError -> {
                            logger.error { "Sent a frame with reference ${frame.callReference} not known by the client! This is probably a bug in the protocol!" }
                        }
                        is AscensionRPCFrame.ProtocolViolationError -> {
                            logger.error { "Sent a frame with reference ${frame.callReference} which was in violation of the protocol. This is probably a bug in the protocol!" }
                        }
                        is AscensionRPCFrame.Upstream -> {
                            handleUpstreamEvent(frame)
                        }
                        is AscensionRPCFrame.Downstream -> {
                            handleDownstreamEvent(frame)
                        }
                        is AscensionRPCFrame.InternalProtocolError.Caller -> {
                            pendingCallees.remove(frame.callReference)?.cancel("Internal protocol error on caller.", frame.throwable.toThrowable())
                        }
                        is AscensionRPCFrame.InternalProtocolError.Callee -> {
                            pendingCallers.remove(frame.callReference)?.cancel("Internal protocol error on callee.", frame.throwable.toThrowable())
                        }
                        is AscensionRPCFrame.SingleCall -> error("PROTOCOL ERROR! Instances of AscensionRPCFrame.SingleCall should either be Upstream or Downstream!")
                        is AscensionRPCFrame.ColdUpstream -> error("PROTOCOL ERROR! Instances of AscensionRPCFrame.ColdUpstream should either be Upstream or Downstream!")
                        is AscensionRPCFrame.ColdDownstream -> error("PROTOCOL ERROR! Instances of AscensionRPCFrame.ColdDownstream should either be Upstream or Downstream!")
                        is AscensionRPCFrame.ColdBistream -> error("PROTOCOL ERROR! Instances of AscensionRPCFrame.ColdBistream should either be Upstream or Downstream!")
                    }
                } catch (t: Throwable) {
                    logger.error(t) { "Error handling frame $frame!" }
                    throw t
                }
                logger.trace { "Did handle frame $frame - $isActive." }
            }
        } finally {
            logger.trace { "Receiving ended" }
        }
    }

    suspend fun send(frame: AscensionRPCFrame) {
        logger.trace { "Sending frame $frame" }
        try {
            connection.send(frameSerializer.serialize(AscensionRPCFrame.serializer(), frame))
        } catch (t: Throwable) {
            logger.error(t) { "Could not send a frame, closing!" }
            coroutineContext.job.cancel("Could not send a frame, closing!", t)
            coroutineContext.job.join()
        }
    }

    private suspend fun <T> handleDownstreamEvent(frame: T) where T: AscensionRPCFrame, T: AscensionRPCFrame.Downstream {
        val pendingCaller = pendingCallers[frame.callReference] ?: return run {
            sendUnknownReferenceError(frame.callReference)
        }
        @Suppress("UNCHECKED_CAST")
        (pendingCaller as PendingRPC.Caller<T, *>).accept(frame)
    }

    private fun calleeScope(reference: RPCReference): CoroutineScope {
        val job = Job(coroutineContext.job)
        job.invokeOnCompletion {
            if (it != null && it !is CancellationException) {
                // TODO: Don't warn when `it` is `CancellationException` or `null`.
                logger.error(it) { "Callee job completed with an unhandled exception!" }
                launch {
                    send(
                        AscensionRPCFrame.InternalProtocolError.Callee(
                            reference,
                            AscensionRPCFrame.InternalProtocolError.SerializableThrowable(it)
                        )
                    )
                }
            } else {
                logger.debug { "Callee job completed for reference $reference." }
            }
            pendingCallees.remove(reference)
        }
        return this + job
    }

    private fun callerScope(reference: RPCReference): CoroutineScope {
        val job = Job(coroutineContext.job)
        job.invokeOnCompletion {
            if (it != null && it !is CancellationException) {
                // TODO: Don't warn when `it` is `CancellationException` or `null`.
                logger.error(it) { "Callee job completed with an unhandled exception!" }
                launch {
                    send(
                        AscensionRPCFrame.InternalProtocolError.Caller(
                            reference,
                            AscensionRPCFrame.InternalProtocolError.SerializableThrowable(it)
                        )
                    )
                }
            } else {
                logger.debug { "Callee job completed for reference $reference." }
            }
            pendingCallers.remove(reference)
        }
        return this + job
    }

    private suspend fun <T> handleUpstreamEvent(frame: T) where T: AscensionRPCFrame, T: AscensionRPCFrame.Upstream {
        val reference = frame.callReference
        val existingPendingCallee = pendingCallees[reference]
        val pendingCallee = when {
            existingPendingCallee != null -> existingPendingCallee
            frame is AscensionRPCFrame.Upstream.Open -> {
                val newPendingCallee = when (frame) {
                    is AscensionRPCFrame.SingleCall -> SingleCallPendingRPC.Callee(
                        this,
                        calleeScope(reference),
                        reference,
                        implementationRegistry.callImplementation(frame.serviceCallIdentifier),
                    )
                    is AscensionRPCFrame.ColdUpstream -> ColdUpstreamPendingRPC.Callee(
                        this,
                        calleeScope(reference),
                        reference,
                        implementationRegistry.callImplementation(frame.serviceCallIdentifier),
                    )
                    is AscensionRPCFrame.ColdDownstream -> ColdDownstreamPendingRPC.Callee(
                        this,
                        calleeScope(reference),
                        reference,
                        implementationRegistry.callImplementation(frame.serviceCallIdentifier),
                    )
                    is AscensionRPCFrame.ColdBistream -> ColdBistreamPendingRPC.Callee(
                        this,
                        calleeScope(reference),
                        reference,
                        implementationRegistry.callImplementation(frame.serviceCallIdentifier),
                    )
                    else -> TODO()
                }
                pendingCallees[reference] = newPendingCallee
                newPendingCallee
            }
            else -> {
                sendUnknownReferenceError(reference)
                return
            }
        }

        @Suppress("UNCHECKED_CAST")
        (pendingCallee as PendingRPC.Callee<T, *>).accept(frame)
    }

    override suspend fun close() {
        logger.trace { "Will close" }
        connection.close()
        logger.trace { "Did close" }
    }

    override suspend fun singleCall(serviceCallIdentifier: ServiceCallIdentifier): RPC.SingleCall.Caller {
        val reference = nextCallReference()
        val pendingCaller = SingleCallPendingRPC.Caller(
            this,
            callerScope(reference),
            serviceCallIdentifier,
            reference,
        )
        pendingCallers[reference] = pendingCaller
        return pendingCaller
    }

    override suspend fun upstream(serviceCallIdentifier: ServiceCallIdentifier): RPC.Upstream.Caller {
        val reference = nextCallReference()
        val pendingCaller = ColdUpstreamPendingRPC.Caller(
            this,
            callerScope(reference),
            serviceCallIdentifier,
            reference,
        )
        pendingCallers[reference] = pendingCaller
        return pendingCaller
    }

    override suspend fun downstream(serviceCallIdentifier: ServiceCallIdentifier): RPC.Downstream.Caller {
        val reference = nextCallReference()
        val pendingCaller = ColdDownstreamPendingRPC.Caller(
            this,
            callerScope(reference),
            serviceCallIdentifier,
            reference,
        )
        pendingCallers[reference] = pendingCaller
        return pendingCaller
    }

    override suspend fun bistream(serviceCallIdentifier: ServiceCallIdentifier): RPC.Bistream.Caller {
        val reference = nextCallReference()
        val pendingCaller = ColdBistreamPendingRPC.Caller(
            this,
            callerScope(reference),
            serviceCallIdentifier,
            reference,
        )
        pendingCallers[reference] = pendingCaller
        return pendingCaller
    }

    private fun nextCallReference(): RPCReference {
        val reference = callReferenceCounter
        callReferenceCounter = RPCReference(reference.reference + 1u)
        return reference
    }

    private suspend fun sendUnknownReferenceError(callReference: RPCReference) {
        send(AscensionRPCFrame.UnknownReferenceError(callReference))
    }

    class Factory: RPCProtocol.Factory {
        override val version = RPCProtocol.Version.Ascension

        override fun create(
            connection: RPCConnection,
            frameSerializer: TransportFrameSerializer,
            implementationRegistry: RPCImplementationRegistry
        ): RPCProtocol {
            return AscensionRPCProtocol(connection, frameSerializer, implementationRegistry)
        }
    }
}
