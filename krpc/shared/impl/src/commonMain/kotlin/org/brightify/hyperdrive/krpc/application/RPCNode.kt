package org.brightify.hyperdrive.krpc.application

public interface RPCNode {
    public val contract: Contract

    public fun <E: RPCNodeExtension> getExtension(identifier: RPCNodeExtension.Identifier<E>): E?

    /**
     * Configuration of this node as agreed upon with the node on the other side.
     */
    public interface Contract {
        public val payloadSerializer: PayloadSerializer
    }
}