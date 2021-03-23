package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.frame.DownstreamRPCEvent
import org.brightify.hyperdrive.krpc.frame.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.description.CallDescription
import org.brightify.hyperdrive.krpc.frame.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.frame.RPCEvent
import org.brightify.hyperdrive.krpc.frame.RPCFrame
import org.brightify.hyperdrive.krpc.util.RPCReference
import org.brightify.hyperdrive.krpc.frame.UpstreamRPCEvent
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.error.RPCProtocolViolationError
import org.brightify.hyperdrive.krpc.error.UnexpectedRPCEventException
import kotlin.coroutines.coroutineContext

// TODO: Add timeout between "Created" and "Ready" to close inactive connections.
abstract class PendingRPC<EVENT: RPCEvent>(
    val connection: RPCConnection,
    val reference: RPCReference,
    private val onFinished: () -> Unit,
) {
    private companion object {
        val logger = Logger<PendingRPC<*>>()
    }

    protected abstract val outgoingErrorEvent: RPCEvent
    protected abstract val outgoingWarningEvent: RPCEvent
    protected abstract val errorSerializer: RPCErrorSerializer

    private val acceptLock = Mutex()
    private lateinit var stateManager: StateManager

    protected abstract suspend fun handle(frame: IncomingRPCFrame<EVENT>)

    suspend fun accept(frame: IncomingRPCFrame<EVENT>) = acceptLock.withLock {
        logger.debug { "Accepting frame: $frame" }
        require(frame.header.callReference == reference) { "Cannot accept frame meant for another call! Frame: $frame, this.reference: $reference." }
        if (this::stateManager.isInitialized && stateManager.isFinished) {
            frame.rejectAsProtocolViolation("Call is closed and doesn't accept new frames.")
            return
        }

        try {
            handle(frame)
        } catch (e: CancellationException) {
            logger.debug { "Frame handler cancelled for $frame." }
            throw e
        } catch (t: Throwable) {
            logger.error(t) { "Frame handler thrown an error!" }
            frame.reject(t)
            cancel("Frame handler thrown an error!", t)
        }
    }

    protected suspend fun retain() {
        getStateManager().retain()
    }

    protected suspend fun release() {
        getStateManager().release()
    }

    protected suspend fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        return getStateManager().launch {
            try {
                block()
            } catch (e: CancellationException) {
                logger.debug { "Frame launched block cancelled." }
                throw e
            } catch (t: Throwable) {
                logger.error(t) { "Frame launched block thrown an error!" }
                doReject(reference, t)
                cancel("Frame launched block thrown an error!", t)
            }
        }
    }

    protected suspend fun <T> run(block: suspend CoroutineScope.() -> T): T {
        return getStateManager().run(block)
    }

    protected suspend fun cancel(reason: String, cause: Throwable? = null) {
        getStateManager().cancel(reason, cause)
    }

    private suspend fun getStateManager(): StateManager {
        if (!this::stateManager.isInitialized) {
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            stateManager = StateManager(scope, onFinished)
        }

        return stateManager
    }

    private class StateManager(
        private val scope: CoroutineScope,
        private val onFinished: () -> Unit
    ) {
        val isFinished: Boolean
            get() = !scope.isActive

        private val lock = Mutex()
        private var retainCount = 0

        suspend fun retain() = lock.withLock {
            logger.trace { "Retaining[${retainCount + 1}]: $scope" }
            scope.ensureActive()
            retainCount += 1
        }

        suspend fun release() = lock.withLock {
            logger.trace { "Releasing[${retainCount - 1}]: $scope" }
            scope.ensureActive()
            require(retainCount > 0) { "Trying to release a call that wasn't retained. Check your retain/release calls." }

            retainCount -= 1

            if (retainCount == 0) {
                logger.trace { "Finishing: $scope" }
                scope.coroutineContext[Job]?.cancelAndJoin()
                onFinished()
            }
        }

        suspend fun launch(block: suspend CoroutineScope.() -> Unit): Job {
            retain()

            return scope.launch {
                try {
                    block()
                } finally {
                    release()
                }
            }
        }

        suspend fun <T> run(block: suspend CoroutineScope.() -> T): T {
            retain()
            try {
                return block(scope)
            } finally {
                release()
            }
        }

        suspend fun cancel(reason: String, cause: Throwable? = null): Unit = lock.withLock {
            logger.warning { "Call $this is being cancelled! Reason: $reason. Cause: $cause." }
            retainCount = 0
            scope.cancel(reason, cause)
            onFinished()
        }
    }

    private suspend fun IncomingRPCFrame<EVENT>.rejectAsProtocolViolation(message: String) {
        val error = RPCProtocolViolationError(message)
        logger.error(error) { "Incoming frame $this has been rejected as protocol violation." }
        doReject(header.callReference, error)
    }

    private suspend fun <ERROR: Throwable> IncomingRPCFrame<EVENT>.reject(error: ERROR) {
        logger.error(error) { "Incoming frame $this has been rejected." }
        doReject(header.callReference, error)
    }

    // We use this method to send the error and cancel this call because other methods log the error too.
    private suspend fun doReject(callReference: RPCReference, error: Throwable) {
        connection.send(
            OutgoingRPCFrame(
                RPCFrame.Header(callReference, outgoingErrorEvent),
                errorSerializer,
                error
            )
        )
    }

    protected suspend fun IncomingRPCFrame<EVENT>.warnUnexpected(reason: String? = null) {
        logger.warning { "Incoming frame $this was not expected, continuing." + (reason?.let { "Reason: $it." } ?: "") }
        connection.send(
            OutgoingRPCFrame(
                RPCFrame.Header(header.callReference, DownstreamRPCEvent.Warning),
                errorSerializer,
                UnexpectedRPCEventException(header.event::class, reason = reason),
            )
        )
    }

    abstract class Server<PAYLOAD, CALL: RunnableCallDescription<PAYLOAD>>(
        connection: RPCConnection,
        reference: RPCReference,
        protected val call: CALL,
        onFinished: () -> Unit,
    ): PendingRPC<UpstreamRPCEvent>(connection, reference, onFinished) {
        override val outgoingErrorEvent = DownstreamRPCEvent.Error
        override val outgoingWarningEvent = DownstreamRPCEvent.Warning

        override val errorSerializer: RPCErrorSerializer = call.errorSerializer

        protected val IncomingRPCFrame<UpstreamRPCEvent>.payload: PAYLOAD
            get() = decoder.decodeSerializableValue(call.payloadSerializer)

        protected val IncomingRPCFrame<UpstreamRPCEvent.Error>.error: RPCError
            get() = decoder.decodeSerializableValue(call.errorSerializer)
    }

    abstract class Client<PAYLOAD, RESPONSE, CALL: CallDescription<PAYLOAD>>(
        connection: RPCConnection,
        reference: RPCReference,
        protected val call: CALL,
        onFinished: () -> Unit,
    ): PendingRPC<DownstreamRPCEvent>(connection, reference, onFinished) {
        override val outgoingErrorEvent = UpstreamRPCEvent.Error
        override val outgoingWarningEvent = UpstreamRPCEvent.Warning

        override val errorSerializer: RPCErrorSerializer = call.errorSerializer

        protected val IncomingRPCFrame<DownstreamRPCEvent.Error>.error: RPCError
            get() = decoder.decodeSerializableValue(call.errorSerializer)

        abstract suspend fun perform(payload: PAYLOAD): RESPONSE

        protected suspend fun open(payload: PAYLOAD) {
            val event = UpstreamRPCEvent.Open(call.identifier)
            val frame = OutgoingRPCFrame(
                header = RPCFrame.Header(
                    callReference = reference,
                    event = event
                ),
                serializationStrategy = call.payloadSerializer,
                payload = payload,
            )

            connection.send(frame)
        }
    }
}