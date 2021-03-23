package org.brightify.hyperdrive.krpc.frame

import kotlinx.serialization.Serializable

@Serializable
sealed class DownstreamRPCEvent: RPCEvent {
    @Serializable
    object Opened: DownstreamRPCEvent() {
        override fun toString(): String = "Downstream.Opened"
    }
    @Serializable
    object Data: DownstreamRPCEvent() {
        override fun toString(): String = "Downstream.Data"
    }
    @Serializable
    object Response: DownstreamRPCEvent() {
        override fun toString(): String = "Downstream.Response"
    }
    @Serializable
    sealed class StreamOperation: DownstreamRPCEvent() {
        @Serializable
        object Start: StreamOperation() {
            override fun toString(): String = "Downstream.StreamOperation.Start"
        }

        @Serializable
        object Close: StreamOperation() {
            override fun toString(): String = "Downstream.StreamOperation.Close"
        }
    }
    @Serializable
    /// Recoverable error happened resulting in a warning.
    object Warning: DownstreamRPCEvent() {
        override fun toString(): String = "Downstream.Warning"
    }
    @Serializable
    /// Irrecoverable error happened
    object Error: DownstreamRPCEvent() {
        override fun toString(): String = "Downstream.Error"
    }
}