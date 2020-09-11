package org.brightify.hyperdrive.krpc.api

interface RPCTransport<INCOMING: RPCEvent, OUTGOING: RPCEvent> {
    suspend fun receive(resolveCall: (RPCFrame.Header<INCOMING>) -> CallDescriptor): RPCFrame<INCOMING>


    suspend fun send(frame: RPCFrame<OUTGOING>)
}
