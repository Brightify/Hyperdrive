package org.brightify.hyperdrive.krpc.test

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.plus
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.SerializedFrame
import org.brightify.hyperdrive.krpc.error.ConnectionClosedException
import kotlin.coroutines.coroutineContext

class LoopbackConnection(
    private val scope: CoroutineScope,
    private val sendDelayInMillis: Long = 0,
    private val receiveDelayInMillis: Long = 0,
): RPCConnection, CoroutineScope by scope + CoroutineName("LoopbackConnection") + SupervisorJob(scope.coroutineContext[Job]) {
    private companion object {
        val logger = Logger<LoopbackConnection>()
    }

    private val channel = Channel<SerializedFrame>(capacity = Channel.UNLIMITED)

    override suspend fun receive(): SerializedFrame {
        try {
            return channel.receive()
                .also {
                    delay(receiveDelayInMillis)
                    logger.debug { "Received $it" }
                }
        } catch (e: ClosedReceiveChannelException) {
            throw ConnectionClosedException()
        }
    }

    override suspend fun send(frame: SerializedFrame) {
        logger.debug {"Sending $frame" }
        delay(sendDelayInMillis)
        channel.send(frame)
    }

    override suspend fun close() {
        logger.trace { "Closing connection $this" }
        channel.close()
        coroutineContext.job.cancel(ConnectionClosedException())
    }
}
