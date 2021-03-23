package org.brightify.hyperdrive.krpc.frame

import kotlinx.serialization.InternalSerializationApi
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

