package org.brightify.hyperdrive.krpc.frame

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier

@Serializable
sealed class UpstreamRPCEvent: RPCEvent {
    @Serializable
    class Open(val serviceCall: ServiceCallIdentifier): UpstreamRPCEvent() {
        override fun toString(): String = "Upstream.Open($serviceCall)"
    }
    @Serializable
    object Data: UpstreamRPCEvent() {
        override fun toString(): String = "Upstream.Data"
    }
    @Serializable
    sealed class StreamOperation: UpstreamRPCEvent() {
        @Serializable
        object Start: StreamOperation() {
            override fun toString(): String = "Upstream.StreamOperation.Start"
        }

        @Serializable
        object Close: StreamOperation() {
            override fun toString(): String = "Upstream.StreamOperation.Close"
        }
    }
    @Serializable
    object Warning: UpstreamRPCEvent() {
        override fun toString(): String = "Upstream.Warning"
    }
    @Serializable
    object Error: UpstreamRPCEvent() {
        override fun toString(): String = "Upstream.Error"
    }
    @Serializable
    object Cancel: UpstreamRPCEvent() {
        override fun toString(): String = "Upstream.Cancel"
    }
}