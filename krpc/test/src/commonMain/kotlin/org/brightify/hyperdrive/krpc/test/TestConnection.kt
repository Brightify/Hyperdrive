package org.brightify.hyperdrive.krpc.test

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.plus
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.SerializedFrame

class TestConnection(
    private val scope: CoroutineScope,
    private val leftToRightDelay: Long = 0,
    private val rightToLeftDelay: Long = 0,
) {
    private companion object {
        val logger = Logger<TestConnection>()
    }

    private val leftToRightFlow = Channel<SerializedFrame>()
    private val rightToLeftFlow = Channel<SerializedFrame>()

    suspend fun close() {
        logger.trace { "Closing connection $this" }
        leftToRightFlow.close()
        rightToLeftFlow.close()
    }

    val left: RPCConnection = object: RPCConnection, CoroutineScope by scope + CoroutineName("TestConnection.left") + SupervisorJob(scope.coroutineContext[Job]) {
        override suspend fun close() {
            this@TestConnection.close()
        }

        override suspend fun receive(): SerializedFrame {
            return rightToLeftFlow.receive().also {
                logger.debug { "<- Received: $it" }
            }
        }

        override suspend fun send(frame: SerializedFrame) {
            logger.debug { "-> Sending: $frame" }
            delay(leftToRightDelay)
            leftToRightFlow.send(frame)
        }

    }
    val right: RPCConnection = object: RPCConnection, CoroutineScope by scope + CoroutineName("TestConnection.right") + SupervisorJob(scope.coroutineContext[Job]) {
        override suspend fun close() {
            this@TestConnection.close()
        }

        override suspend fun receive(): SerializedFrame {
            return leftToRightFlow.receive().also {
                logger.debug { "-> Received: $it" }
            }
        }

        override suspend fun send(frame: SerializedFrame) {
            logger.debug { "<- Sending: $frame" }
            delay(rightToLeftDelay)
            rightToLeftFlow.send(frame)
        }

    }
}