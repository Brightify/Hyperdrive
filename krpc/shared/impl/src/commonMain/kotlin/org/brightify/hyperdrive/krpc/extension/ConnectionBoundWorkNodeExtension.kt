package org.brightify.hyperdrive.krpc.extension

import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.application.RPCNode
import org.brightify.hyperdrive.krpc.application.RPCNodeExtension

public class ConnectionBoundWorkNodeExtension(
    private val parallelWork: suspend () -> Unit
): RPCNodeExtension {
    override suspend fun bind(transport: RPCTransport, contract: RPCNode.Contract) { }

    override suspend fun whileConnected() {
        parallelWork.invoke()
    }
}