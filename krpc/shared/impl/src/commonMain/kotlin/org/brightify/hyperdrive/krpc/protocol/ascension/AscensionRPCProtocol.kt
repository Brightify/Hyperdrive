package org.brightify.hyperdrive.krpc.protocol.ascension

import co.touchlab.stately.ensureNeverFrozen
import co.touchlab.stately.concurrency.Lock
import co.touchlab.stately.concurrency.withLock
import kotlinx.coroutines.*
import kotlinx.serialization.SerializationException
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.error.ConnectionClosedException
import org.brightify.hyperdrive.krpc.protocol.RPCProtocol
import org.brightify.hyperdrive.krpc.util.RPCReference
import org.brightify.hyperdrive.krpc.frame.AscensionRPCFrame
import org.brightify.hyperdrive.krpc.protocol.RPC
import org.brightify.hyperdrive.krpc.protocol.RPCImplementationRegistry
import org.brightify.hyperdrive.krpc.protocol.callImplementation
import org.brightify.hyperdrive.krpc.transport.TransportFrameSerializer
import org.brightify.hyperdrive.utils.Do

class AscensionRPCProtocol(
    private val connection: RPCConnection,
    private val frameSerializer: TransportFrameSerializer,
    private val implementationRegistry: RPCImplementationRegistry,
): RPCProtocol {
    private companion object {
        val logger = Logger<AscensionRPCProtocol>()
    }

    override val version = RPCProtocol.Version.Ascension

    private class SynchronizedAccess<T>(private val value: T) {
        private val lock = Lock()

        inline fun <U> access(block: T.() -> U): U = lock.withLock {
            value.block()
        }
    }

    private val pendingCallees = SynchronizedAccess(mutableMapOf<RPCReference, PendingRPC.Callee<*, *>>().apply { ensureNeverFrozen() })
    private val pendingCallers = SynchronizedAccess(mutableMapOf<RPCReference, PendingRPC.Caller<*, *>>().apply { ensureNeverFrozen() })

    // TODO: Replace with AtomicInt
    private var callReferenceCounter: RPCReference = RPCReference(UInt.MIN_VALUE)

    override suspend fun run() {
        logger.trace { "Receiving started" }

        try {
            while (connection.isActive) {
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
                    when (frame) {
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
                            pendingCallees.access { remove(frame.callReference) }
                                ?.cancel("Internal protocol error on caller.", frame.throwable.toThrowable())
                        }
                        is AscensionRPCFrame.InternalProtocolError.Callee -> {
                            pendingCallers.access { remove(frame.callReference) }
                                ?.cancel("Internal protocol error on callee.", frame.throwable.toThrowable())
                        }
                    }
                } catch (t: Throwable) {
                    logger.error(t) { "Error handling frame $frame!" }
                    throw t
                }
                logger.trace { "Did handle frame $frame - ${connection.isActive}." }
            }
            logger.trace { "Receiving ended." }
            cancelPendingRPCs(ConnectionClosedException())
        } catch (t: CancellationException) {
            logger.debug { "Receiving cancelled." }
            cancelPendingRPCs(t)
            throw t
        } catch (t: Throwable) {
            logger.debug(t) { "Receiving failed." }
            cancelPendingRPCs(CancellationException("Receiving failed", t))
            throw t
        }
    }

    private fun cancelPendingRPCs(cause: CancellationException) {
        try {
            logger.trace { "Cancelling pending callers." }
            val copiedPendingCallers = pendingCallers.access {
                val list = values.toList()
                clear()
                list
            }
            copiedPendingCallers.forEach {
                try {
                    it.cancel(cause)
                } catch (t: Throwable) {
                    logger.warning(t) { "Couldn't cancel caller $it." }
                }
            }

            logger.trace { "Pending callers canceled." }
        } catch (t: Throwable) {
            logger.warning(t) { "Couldn't cancel callers!" }
        }

        try {
            logger.trace { "Cancelling pending callees." }

            val copiedPendingCallees = pendingCallees.access {
                val list = values.toList()
                clear()
                list
            }

            copiedPendingCallees.forEach {
                try {
                    it.cancel(cause)
                } catch (t: Throwable) {
                    logger.warning(t) { "Couldn't cancel calee $it." }
                }
            }

            logger.trace { "Pending callees canceled." }
        } catch (t: Throwable) {
            logger.warning(t) { "Couldn't cancel callees!" }
        }

    }

    suspend fun send(frame: AscensionRPCFrame) {
        logger.trace { "Sending frame $frame" }
        try {
            connection.send(frameSerializer.serialize(AscensionRPCFrame.serializer(), frame))
        } catch (t: Throwable) {
            logger.error(t) { "Could not send a frame!" }
            throw t
        }
    }

    private suspend fun <T> handleDownstreamEvent(frame: T) where T: AscensionRPCFrame, T: AscensionRPCFrame.Downstream {
        val pendingCaller = pendingCallers.access { this[frame.callReference] } ?: return run {
            sendUnknownReferenceError(frame.callReference)
        }
        @Suppress("UNCHECKED_CAST")
        (pendingCaller as PendingRPC.Caller<T, *>).accept(frame)
    }

    private suspend fun <T> handleUpstreamEvent(frame: T) where T: AscensionRPCFrame, T: AscensionRPCFrame.Upstream {
        val reference = frame.callReference
        val existingPendingCallee = pendingCallees.access { this[reference] }
        val pendingCallee = when {
            existingPendingCallee != null -> existingPendingCallee
            frame is AscensionRPCFrame.Upstream.Open -> {
                val newPendingCallee = when (frame) {
                    is AscensionRPCFrame.SingleCall -> SingleCallPendingRPC.Callee(
                        this,
                        connection,
                        reference,
                        implementationRegistry.callImplementation(frame.serviceCallIdentifier),
                    )
                    is AscensionRPCFrame.ColdUpstream -> ColdUpstreamPendingRPC.Callee(
                        this,
                        connection,
                        reference,
                        implementationRegistry.callImplementation(frame.serviceCallIdentifier),
                    )
                    is AscensionRPCFrame.ColdDownstream -> ColdDownstreamPendingRPC.Callee(
                        this,
                        connection,
                        reference,
                        implementationRegistry.callImplementation(frame.serviceCallIdentifier),
                    )
                    is AscensionRPCFrame.ColdBistream -> ColdBistreamPendingRPC.Callee(
                        this,
                        connection,
                        reference,
                        implementationRegistry.callImplementation(frame.serviceCallIdentifier),
                    )
                }
                pendingCallees.access { this[reference] = newPendingCallee }
                newPendingCallee.invokeOnCompletion {
                    try {
                        if (it != null && it !is CancellationException) {
                            // TODO: Don't warn when `it` is `CancellationException` or `null`.
                            logger.error(it) { "Callee job ($reference) completed with an unhandled exception!" }
                            if (connection.isActive) {
                                connection.launch {
                                    send(
                                        AscensionRPCFrame.InternalProtocolError.Callee(
                                            reference,
                                            AscensionRPCFrame.InternalProtocolError.SerializableThrowable(it)
                                        )
                                    )
                                }
                            }
                        } else {
                            logger.debug { "Callee job ($reference) completed." }
                        }
                    } finally {
                        pendingCallees.access { remove(reference) }
                    }
                }
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

    private fun <T: PendingRPC.Caller<*, *>> managedCaller(factory: (RPCReference) -> T): T {
        val reference = nextCallReference()
        val pendingRpc = factory(reference)
        pendingCallers.access { this[reference] = pendingRpc }
        pendingRpc.invokeOnCompletion {
            try {
                if (it != null && it !is CancellationException) {
                    // TODO: Don't warn when `it` is `CancellationException` or `null`.
                    logger.error(it) { "Caller job completed with an unhandled exception!" }
                    connection.launch {
                        send(
                            AscensionRPCFrame.InternalProtocolError.Caller(
                                reference,
                                AscensionRPCFrame.InternalProtocolError.SerializableThrowable(it)
                            )
                        )
                    }
                } else {
                    logger.debug { "Caller job completed for reference $reference." }
                }
            } finally {
                pendingCallers.access { remove(reference) }
            }
        }
        return pendingRpc
    }

    override fun singleCall(serviceCallIdentifier: ServiceCallIdentifier): RPC.SingleCall.Caller = managedCaller { reference ->
        SingleCallPendingRPC.Caller(
            this,
            connection,
            serviceCallIdentifier,
            reference,
        )
    }

    override fun upstream(serviceCallIdentifier: ServiceCallIdentifier): RPC.Upstream.Caller = managedCaller { reference ->
        ColdUpstreamPendingRPC.Caller(
            this,
            connection,
            serviceCallIdentifier,
            reference,
        )
    }

    override fun downstream(serviceCallIdentifier: ServiceCallIdentifier): RPC.Downstream.Caller = managedCaller { reference ->
        ColdDownstreamPendingRPC.Caller(
            this,
            connection,
            serviceCallIdentifier,
            reference,
        )
    }

    override fun bistream(serviceCallIdentifier: ServiceCallIdentifier): RPC.Bistream.Caller = managedCaller { reference ->
        ColdBistreamPendingRPC.Caller(
            this,
            connection,
            serviceCallIdentifier,
            reference,
        )
    }

    private fun nextCallReference(): RPCReference {
        val reference = callReferenceCounter
        callReferenceCounter = RPCReference(reference.reference + 1u)
        return reference
    }

    private suspend fun sendUnknownReferenceError(callReference: RPCReference) {
        send(AscensionRPCFrame.UnknownReferenceError(callReference))
    }

    public class Factory: RPCProtocol.Factory {
        override val version: RPCProtocol.Version = RPCProtocol.Version.Ascension

        override fun create(
            connection: RPCConnection,
            frameSerializer: TransportFrameSerializer,
            implementationRegistry: RPCImplementationRegistry
        ): RPCProtocol {
            return AscensionRPCProtocol(connection, frameSerializer, implementationRegistry)
        }
    }
}
