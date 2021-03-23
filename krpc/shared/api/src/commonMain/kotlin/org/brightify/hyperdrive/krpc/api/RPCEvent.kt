package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

interface RPCEvent {

    companion object {
        @OptIn(InternalSerializationApi::class)
        val serializersModule = SerializersModule {
            polymorphic(RPCEvent::class) {
                subclass(UpstreamRPCEvent::class, UpstreamRPCEvent.serializer())
                subclass(UpstreamRPCEvent.Open::class, UpstreamRPCEvent.Open.serializer())
                subclass(UpstreamRPCEvent.Data::class, UpstreamRPCEvent.Data.serializer())
                subclass(UpstreamRPCEvent.Error::class, UpstreamRPCEvent.Error.serializer())
                subclass(UpstreamRPCEvent.StreamOperation.Start::class, UpstreamRPCEvent.StreamOperation.Start.serializer())
                subclass(UpstreamRPCEvent.StreamOperation.Close::class, UpstreamRPCEvent.StreamOperation.Close.serializer())

                subclass(DownstreamRPCEvent::class, DownstreamRPCEvent.serializer())
                subclass(DownstreamRPCEvent.Opened::class, DownstreamRPCEvent.Opened.serializer())
                subclass(DownstreamRPCEvent.Data::class, DownstreamRPCEvent.Data.serializer())
                subclass(DownstreamRPCEvent.Response::class, DownstreamRPCEvent.Response.serializer())
                subclass(DownstreamRPCEvent.StreamOperation.Start::class, DownstreamRPCEvent.StreamOperation.Start.serializer())
                subclass(DownstreamRPCEvent.StreamOperation.Close::class, DownstreamRPCEvent.StreamOperation.Close.serializer())
                subclass(DownstreamRPCEvent.Warning::class, DownstreamRPCEvent.Warning.serializer())
                subclass(DownstreamRPCEvent.Error::class, DownstreamRPCEvent.Error.serializer())
            }
        }
    }
}

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
