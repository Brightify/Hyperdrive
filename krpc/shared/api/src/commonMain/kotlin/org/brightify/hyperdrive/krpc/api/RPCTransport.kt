package org.brightify.hyperdrive.krpc.api

interface RPCTransport<OUTGOING: RPCEvent, INCOMING: RPCEvent> {
    suspend fun receive(): IncomingRPCFrame<INCOMING>

    suspend fun send(frame: OutgoingRPCFrame<OUTGOING>)
}
