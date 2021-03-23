package org.brightify.hyperdrive.krpc

import kotlinx.coroutines.CoroutineScope
import org.brightify.hyperdrive.krpc.frame.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.frame.RPCEvent
import org.brightify.hyperdrive.krpc.frame.OutgoingRPCFrame

interface RPCConnection: CoroutineScope {
    suspend fun close()

    suspend fun receive(): IncomingRPCFrame<RPCEvent>

    suspend fun send(frame: OutgoingRPCFrame<RPCEvent>)
}