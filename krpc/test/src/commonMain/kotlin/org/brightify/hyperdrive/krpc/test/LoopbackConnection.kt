package org.brightify.hyperdrive.krpc.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.serialization.modules.plus
import kotlinx.serialization.protobuf.ProtoBuf
import org.brightify.hyperdrive.krpc.frame.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.frame.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.frame.RPCEvent
import org.brightify.hyperdrive.krpc.frame.serialization.RPCFrameDeserializationStrategy
import org.brightify.hyperdrive.krpc.frame.serialization.RPCFrameSerializationStrategy

class LoopbackConnection(
    private val scope: CoroutineScope,
    private val sendDelayInMillis: Long = 0,
): RPCConnection, CoroutineScope by scope {

    private val outgoingSerializer = RPCFrameSerializationStrategy<RPCEvent>()
    private val incomingDeserializer = RPCFrameDeserializationStrategy<RPCEvent>()
    private val channel = Channel<ByteArray>(capacity = Channel.UNLIMITED)

    private val format = ProtoBuf {
        serializersModule += RPCEvent.serializersModule
    }

    override suspend fun receive(): IncomingRPCFrame<RPCEvent> {
        return channel.receive()
            .let { format.decodeFromByteArray(incomingDeserializer, it) }
            .also { println("Received $it") }
    }

    override suspend fun send(frame: OutgoingRPCFrame<RPCEvent>) {
        println("Sending $frame")
        delay(sendDelayInMillis)
        channel.send(format.encodeToByteArray(outgoingSerializer, frame))
    }

    override suspend fun close() {
        scope.coroutineContext[Job]?.cancel()
    }
}