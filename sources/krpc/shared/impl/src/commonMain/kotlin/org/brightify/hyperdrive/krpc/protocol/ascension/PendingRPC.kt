package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.error.RPCProtocolViolationError
import org.brightify.hyperdrive.krpc.frame.AscensionRPCFrame
import org.brightify.hyperdrive.krpc.util.RPCReference
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

// TODO: Add timeout between "Created" and "Ready" to close inactive connections.
public abstract class PendingRPC<INCOMING: AscensionRPCFrame, OUTGOING: AscensionRPCFrame>(
    public val protocol: AscensionRPCProtocol,
    context: CoroutineContext,
    public val reference: RPCReference,
    private val logger: Logger,
): CoroutineScope {
    protected val completionTracker: CompletionTracker = CompletionTracker()

    private val mainJob = Job(context[Job])
    override val coroutineContext: CoroutineContext = context + CoroutineName("PendingRPC") + mainJob

    protected abstract val shouldComplete: Flow<Boolean>

    protected abstract suspend fun handle(frame: INCOMING)

    // TODO: Increase buffer capacity so the `accept` doesn't wait for the handler unless buffer's full.
    private val acceptQueue = Channel<INCOMING>()

    public fun initialize(onCompletion: CompletionHandler) {
        mainJob.invokeOnCompletion(onCompletion)

        launch {
            for (frame in acceptQueue) {
                logger.trace { "Will handle: $frame" }
                handle(frame)
                logger.trace { "Did handle: $frame" }
            }
        }

        launch {
            shouldComplete.first { it }
            complete()
        }
    }

    public suspend fun accept(frame: INCOMING) {
        logger.debug { "Accepting frame: $frame" }
        require(frame.callReference == reference) {
            "Cannot accept frame meant for another call! Frame: $frame, this.reference: $reference."
        }

        try {
            acceptQueue.send(frame)
        } catch (e: ClosedSendChannelException) {
            logger.warning { "PendingRPC has completed, dropping frame: $frame" }
        }
    }

    protected suspend fun send(frame: OUTGOING) {
        protocol.send(frame)
    }

    protected val <T> CompletableDeferred<T>.isCompletedFlow: Flow<Boolean>
        get() = flow {
            try {
                await()
            } catch (t: Throwable) {
                // Intentionally ignored as we only care about the completion state.
            } finally {
                emit(true)
            }
        }

    protected fun newPayloadChannel(): Channel<SerializedPayload> {
        return Channel<SerializedPayload>().also { channel ->
            mainJob.invokeOnCompletion {
                channel.close(it)
            }
        }
    }

    private fun complete() {
        logger.trace { "Completing RPC($reference): $this" }
        acceptQueue.close()
        mainJob.complete()
    }

    protected inner class CompletionTracker {
        private var acquiredTokens = 0

        public fun launch(start: CoroutineStart = CoroutineStart.DEFAULT, block: suspend CoroutineScope.() -> Unit): Job {
            val token = acquire()
            val job = this@PendingRPC.launch(start = start, block = block)
            job.invokeOnCompletion { 
                token.release()
            }
            return job
        }

        public suspend fun <T> tracking(block: suspend CoroutineScope.() -> T): T {
            val token = acquire()
            try {
                return block()
            } finally {
                token.release()
            }
        }

        @OptIn(ExperimentalTime::class)
        public fun wait(duration: Duration): Job = launch {
            delay(duration)
        }

        @OptIn(ExperimentalTime::class)
        public suspend fun <T> withTimeout(duration: Duration, block: suspend () -> T): T = coroutineScope {
            val token = acquire()
            try {
                withTimeout(duration, block)
            } finally {
                token.release()
            }
        }

        public fun acquire(): Token {
            check(acquiredTokens >= 0) { "CompletionTracker has already completed the PendingRPC!" }
            acquiredTokens += 1
            return Token()
        }

        private fun release() {
            acquiredTokens -= 1
            if (acquiredTokens <= 0) {
                acquiredTokens = releasedTokenValue
                // complete()
            }
        }

        public inner class Token {
            private var isReleased = false

            public fun release() {
                check(!isReleased) { "Token cannot be released multiple times." }
                this@CompletionTracker.release()
            }
        }
    }

    private companion object {
        // A negative value constant to mark the tracker as released.
        const val releasedTokenValue = -0xdead
    }

    public abstract class Callee<INCOMING, OUTGOING>(
        protocol: AscensionRPCProtocol,
        context: CoroutineContext,
        reference: RPCReference,
        logger: Logger,
    ): PendingRPC<INCOMING, OUTGOING>(protocol, context, reference, logger)
        where INCOMING: AscensionRPCFrame, INCOMING: AscensionRPCFrame.Upstream,
              OUTGOING: AscensionRPCFrame, OUTGOING: AscensionRPCFrame.Downstream

    public abstract class Caller<INCOMING, OUTGOING>(
        protocol: AscensionRPCProtocol,
        context: CoroutineContext,
        reference: RPCReference,
        logger: Logger,
    ): PendingRPC<INCOMING, OUTGOING>(protocol, context, reference, logger)
        where INCOMING: AscensionRPCFrame, INCOMING: AscensionRPCFrame.Downstream,
              OUTGOING: AscensionRPCFrame, OUTGOING: AscensionRPCFrame.Upstream
}