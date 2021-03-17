package org.brightify.hyperdrive.krpc.api.impl

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.DownstreamRPCEvent
import org.brightify.hyperdrive.krpc.api.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.api.LocalCallDescriptor
import org.brightify.hyperdrive.krpc.api.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.api.RPCConnection
import org.brightify.hyperdrive.krpc.api.RPCEvent
import org.brightify.hyperdrive.krpc.api.RPCFrame
import org.brightify.hyperdrive.krpc.api.RPCReference
import org.brightify.hyperdrive.krpc.api.UnexpectedRPCEventException
import org.brightify.hyperdrive.krpc.api.UpstreamRPCEvent
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.api.error.RPCProtocolViolationError
import kotlin.coroutines.coroutineContext

// TODO: Add timeout between "Created" and "Ready" to close inactive connections.
abstract class _PendingRPC<EVENT: RPCEvent>(
    val connection: RPCConnection,
    val reference: RPCReference,
    private val onFinished: () -> Unit,
) {
    private companion object {
        val logger = Logger<_PendingRPC<*>>()
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
        return getStateManager().launch(block)
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
        doReject(error)
    }

    private suspend fun <ERROR: Throwable> IncomingRPCFrame<EVENT>.reject(error: ERROR) {
        logger.error(error) { "Incoming frame $this has been rejected." }
        doReject(error)
    }

    // We use this method to send the error and cancel this call because other methods log the error too.
    private suspend fun IncomingRPCFrame<EVENT>.doReject(error: Throwable) {
        connection.send(
            OutgoingRPCFrame(
                RPCFrame.Header(header.callReference, outgoingErrorEvent),
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

    abstract class Server<PAYLOAD, CALL: CallDescriptor<PAYLOAD>>(
        connection: RPCConnection,
        reference: RPCReference,
        protected val call: CALL,
        onFinished: () -> Unit,
    ): _PendingRPC<UpstreamRPCEvent>(connection, reference, onFinished) {
        override val outgoingErrorEvent = DownstreamRPCEvent.Error
        override val outgoingWarningEvent = DownstreamRPCEvent.Warning

        override val errorSerializer: RPCErrorSerializer = call.errorSerializer

        protected val IncomingRPCFrame<UpstreamRPCEvent>.payload: PAYLOAD
            get() = decoder.decodeSerializableValue(call.payloadSerializer)

        protected val IncomingRPCFrame<UpstreamRPCEvent.Error>.error: Throwable
            get() = decoder.decodeSerializableValue(call.errorSerializer)
    }

    abstract class Client<PAYLOAD, RESPONSE, CALL: LocalCallDescriptor<PAYLOAD>>(
        connection: RPCConnection,
        reference: RPCReference,
        protected val call: CALL,
        onFinished: () -> Unit,
    ): _PendingRPC<DownstreamRPCEvent>(connection, reference, onFinished) {
        override val outgoingErrorEvent = UpstreamRPCEvent.Error
        override val outgoingWarningEvent = UpstreamRPCEvent.Warning

        override val errorSerializer: RPCErrorSerializer = call.errorSerializer

        protected val IncomingRPCFrame<DownstreamRPCEvent.Error>.error: Throwable
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

    // class _StateManager {
    //     sealed class State {
    //         class Ready(val runningJob: Job): State()
    //         object Closed: State()
    //     }
    //
    //     var state: State = State.Ready(Job())
    //         private set
    //
    //     private val observers: MutableMap<State, CompletableDeferred<Unit>> = mutableMapOf()
    //
    //     private val lock = Mutex()
    //
    //     suspend fun setOpened(runningJob: Job? = null) = lock.withLock {
    //         require(state == State.Created) { "Required to be just created, was $state" }
    //
    //         state = State.Ready(runningJob)
    //
    //         notifyStateChanged()
    //     }
    //
    //     suspend fun attachToOpened(block: suspend CoroutineScope.() -> Unit) = lock.withLock {
    //         val state = state
    //         require(state is State.Ready) { "Required to be in a ready state, was $state." }
    //
    //         if (state.runningJob == null) {
    //             val job = coroutineScope {
    //                 launch(start = CoroutineStart.LAZY) { block() }
    //             }
    //             this.state = State.Ready(job)
    //             job.start()
    //         } else {
    //             coroutineScope {
    //                 launch(state.runningJob) {
    //                     block()
    //                 }
    //             }.also { it.start() }
    //         }
    //     }
    //
    //     suspend fun setClosed(reason: String, cause: Throwable? = null) = lock.withLock {
    //         val state = state
    //         require(state is State.Ready) { "Required to be in a ready state, was $state" }
    //
    //         state.runningJob?.cancel(reason, cause)
    //         state.runningJob?.join()
    //
    //         this.state = State.Closed
    //
    //         notifyStateChanged()
    //     }
    //
    //     // TODO: Is this still needed?
    //     suspend fun await(state: State) {
    //         if (this.state == state) {
    //             return
    //         } else {
    //             val observer = observers.getOrPut(state, ::CompletableDeferred)
    //             observer.await()
    //         }
    //     }
    //
    //     private fun notifyStateChanged() {
    //         val stateObserver = observers.remove(state) ?: return
    //         stateObserver.complete(Unit)
    //     }
    // }
}

interface PendingRPC<OUTGOING, INCOMING> {
    val descriptor: LocalCallDescriptor<OUTGOING>

    val payloadSerializationStrategy: SerializationStrategy<OUTGOING>

    val payload: OUTGOING

    val stateManager: StateManager

    val deserializationStrategy: DeserializationStrategy<INCOMING>

    val errorSerializer: RPCErrorSerializer

    suspend fun accept(data: INCOMING): Boolean

    suspend fun dataEnd()

    suspend fun reject(throwable: Throwable)

    suspend fun close(throwable: Throwable?): Boolean

    enum class State {
        Closed,
        Busy,
        Ready,
    }

    class StateManager {
        var state = State.Closed
            private set

        private val observers: MutableMap<State, MutableSet<CompletableDeferred<Unit>>> = mutableMapOf()

        fun setOpened() {
            require(state == State.Closed) { "Required to be closed, was $state" }

            state = State.Ready

            notifyStateChanged()
        }

        fun setBusy() {
            require(state == State.Ready)

            state = State.Busy

            notifyStateChanged()
        }

        fun setReady() {
            require(state == State.Busy)

            state = State.Ready

            notifyStateChanged()
        }

        suspend fun await(state: State) {
            if (this.state == state) {
                return
            } else {
                val observer = CompletableDeferred<Unit>()
                observers.getOrPut(state, ::mutableSetOf).add(observer)
                observer.await()
            }
        }

        private fun notifyStateChanged() {
            val state = this.state
            val stateObservers = observers.remove(state) ?: return

            for (observer in stateObservers) {
                observer.complete(Unit)
            }
        }
    }
}