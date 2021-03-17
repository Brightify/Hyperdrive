package org.brightify.hyperdrive.krpc.api

import kotlinx.coroutines.CoroutineScope

interface RPCConnection: CoroutineScope {
    suspend fun close()

    suspend fun receive(): IncomingRPCFrame<RPCEvent>

    suspend fun send(frame: OutgoingRPCFrame<RPCEvent>)
}