package org.brightify.hyperdrive.krpc

import org.brightify.hyperdrive.krpc.application.RPCNode
import org.brightify.hyperdrive.krpc.application.RPCNodeExtension

class ConnectionBoundWorkNodeExtension(
    private val parallelWork: suspend () -> Unit
): RPCNodeExtension {
    override suspend fun bind(transport: RPCTransport, contract: RPCNode.Contract) { }

    override suspend fun parallelWork() {
        parallelWork.invoke()
    }
}