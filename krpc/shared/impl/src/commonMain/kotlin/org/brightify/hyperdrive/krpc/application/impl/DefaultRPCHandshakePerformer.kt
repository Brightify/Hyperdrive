package org.brightify.hyperdrive.krpc.application.impl

import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.SerializedFrame
import org.brightify.hyperdrive.krpc.frame.HandshakeRPCFrame
import org.brightify.hyperdrive.krpc.protocol.RPCProtocol
import org.brightify.hyperdrive.krpc.protocol.ascension.AscensionRPCProtocol
import org.brightify.hyperdrive.krpc.application.RPCHandshakePerformer
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer
import org.brightify.hyperdrive.utils.Do

public class DefaultRPCHandshakePerformer(
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

    override suspend fun performHandshake(connection: RPCConnection): RPCHandshakePerformer.HandshakeResult =
        withTimeout(totalHandshakeTimeoutInMillis) {
            try {
                assertHandshakeVersion(connection)
                val selectedSerializationFormat = selectSerializationFormat(connection)
                val selectedFrameSerializer = frameSerializerFactory.create(selectedSerializationFormat)
                val selectedProtocolFactory = selectProtocol(connection, selectedFrameSerializer)
                RPCHandshakePerformer.HandshakeResult.Success(selectedFrameSerializer, selectedProtocolFactory)
            } catch (e: SerializationException) {
                logger.error(e) { "Couldn't complete handshake because of a serialization issue." }
                RPCHandshakePerformer.HandshakeResult.Failed(e.message ?: "Unknown serialization failure during handshake.")
            } catch (e: HandshakeFailedException) {
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

    public enum class Behavior {
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