package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.error.RPCProtocolViolationError
import org.brightify.hyperdrive.krpc.frame.AscensionRPCFrame
import org.brightify.hyperdrive.krpc.util.RPCReference

// TODO: Add timeout between "Created" and "Ready" to close inactive connections.
public abstract class PendingRPC<INCOMING: AscensionRPCFrame, OUTGOING: AscensionRPCFrame>(
    public val protocol: AscensionRPCProtocol,
    scope: CoroutineScope,
    public val reference: RPCReference,
    private val logger: Logger,
): CoroutineScope by scope + CoroutineName("PendingRPC") {
    protected abstract suspend fun handle(frame: INCOMING)

    // TODO: Increase buffer capacity so the `accept` doesn't wait for the handler unless buffer's full.
    private val acceptQueue = Channel<INCOMING>()

    private val runningJob = launch {
        for (frame in acceptQueue) {
            logger.trace { "Will handle: $frame" }
            handle(frame)
            logger.trace { "Did handle: $frame" }
        }
    }

    public fun invokeOnCompletion(handler: CompletionHandler): DisposableHandle = runningJob.invokeOnCompletion(handler)

    public suspend fun accept(frame: INCOMING) {
        runningJob.ensureActive()
        logger.debug { "Accepting frame: $frame" }
        require(frame.callReference == reference) {
            "Cannot accept frame meant for another call! Frame: $frame, this.reference: $reference."
        }

        acceptQueue.send(frame)
    }

    protected suspend fun send(frame: OUTGOING) {
        protocol.send(frame)
    }

    protected fun complete() {
        acceptQueue.close()
    }

    private suspend fun rejectAsProtocolViolation(message: String) {
        val error = RPCProtocolViolationError(message)
        logger.error(error) { "Incoming frame $this has been rejected as protocol violation." }
        protocol.send(AscensionRPCFrame.ProtocolViolationError(reference, message))
    }

    public abstract class Callee<INCOMING, OUTGOING>(
        protocol: AscensionRPCProtocol,
        scope: CoroutineScope,
        reference: RPCReference,
        logger: Logger,
    ): PendingRPC<INCOMING, OUTGOING>(protocol, scope, reference, logger)
        where INCOMING: AscensionRPCFrame, INCOMING: AscensionRPCFrame.Upstream,
              OUTGOING: AscensionRPCFrame, OUTGOING: AscensionRPCFrame.Downstream

    public abstract class Caller<INCOMING, OUTGOING>(
        protocol: AscensionRPCProtocol,
        scope: CoroutineScope,
        reference: RPCReference,
        logger: Logger,
    ): PendingRPC<INCOMING, OUTGOING>(protocol, scope, reference, logger)
        where INCOMING: AscensionRPCFrame, INCOMING: AscensionRPCFrame.Downstream,
              OUTGOING: AscensionRPCFrame, OUTGOING: AscensionRPCFrame.Upstream
}