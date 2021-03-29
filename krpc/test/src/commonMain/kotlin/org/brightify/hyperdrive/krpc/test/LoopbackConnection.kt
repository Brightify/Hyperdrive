package org.brightify.hyperdrive.krpc.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.plus
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.SerializedFrame

class LoopbackConnection(
    private val scope: CoroutineScope,
    private val sendDelayInMillis: Long = 0,
    private val receiveDelayInMillis: Long = 0,
): RPCConnection, CoroutineScope by scope.plus(Job(scope.coroutineContext[Job])) {
    private companion object {
        val logger = Logger<LoopbackConnection>()
    }

    private val channel = Channel<SerializedFrame>(capacity = Channel.UNLIMITED)

    override suspend fun receive(): SerializedFrame {
        return channel.receive()
            .also {
                delay(receiveDelayInMillis)
                logger.debug { "Received $it" }
            }
    }

    override suspend fun send(frame: SerializedFrame) {
        logger.debug {"Sending $frame" }
        delay(sendDelayInMillis)
        channel.send(frame)
    }

    override suspend fun close() {
        scope.coroutineContext[Job]?.cancelAndJoin()
    }
}
