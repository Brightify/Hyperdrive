package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

interface RPCEvent {

    companion object {
        @OptIn(InternalSerializationApi::class)
        val serializersModule = SerializersModule {
            polymorphic(RPCEvent::class) {
                // TODO: Once reflection API supports `sealedSubclasses` on non-JVM platforms, use it instead of listing all the classes.
                subclass(ContextUpdateRPCEvent::class)

                subclass(UpstreamRPCEvent::class)
                subclass(UpstreamRPCEvent.Open::class)
                subclass(UpstreamRPCEvent.Data::class)
                subclass(UpstreamRPCEvent.Error::class)
                subclass(UpstreamRPCEvent.StreamOperation.Start::class)
                subclass(UpstreamRPCEvent.StreamOperation.Close::class)

                subclass(DownstreamRPCEvent::class)
                subclass(DownstreamRPCEvent.Opened::class)
                subclass(DownstreamRPCEvent.Data::class)
                subclass(DownstreamRPCEvent.Response::class)
                subclass(DownstreamRPCEvent.StreamOperation.Start::class)
                subclass(DownstreamRPCEvent.StreamOperation.Close::class)
                subclass(DownstreamRPCEvent.Warning::class)
                subclass(DownstreamRPCEvent.Error::class)
            }
        }
    }
}

@Serializable
object ContextUpdateRPCEvent: RPCEvent

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
