package org.brightify.hyperdrive.krpc.application

import org.brightify.hyperdrive.krpc.protocol.ascension.PayloadSerializer

interface RPCNode {
    val contract: Contract

    fun <E: RPCNodeExtension> getExtension(identifier: RPCNodeExtension.Identifier<E>): E?

    /**
     * Configuration of this node as agreed upon with the node on the other side.
     */
    interface Contract {
        val payloadSerializer: PayloadSerializer
    }
}