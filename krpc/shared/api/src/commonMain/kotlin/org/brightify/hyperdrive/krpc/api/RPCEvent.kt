package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.Serializable

typealias RPCReference = Int

@Serializable
data class ServiceCallIdentifier(
    val serviceId: String,
    val callId: String
)

@Serializable
sealed class RPCEvent {
    @Serializable
    sealed class Upstream: RPCEvent() {
        @Serializable
        class Open(val serviceCall: ServiceCallIdentifier): Upstream()
        @Serializable
        object Data: Upstream()
        @Serializable
        object Error: Upstream()
        @Serializable
        object Close: Upstream()
    }

    @Serializable
    sealed class Downstream: RPCEvent() {
        @Serializable
        object Opened: Downstream()
        @Serializable
        object Data: Downstream()
        @Serializable
        object Response: Downstream()
        @Serializable
        object Close: Downstream()
        @Serializable
        object Warning: Downstream()
        @Serializable
        object Error: Downstream()
    }
}

//@Serializable
//sealed class RPCEvent {
//    @Serializable
//    sealed class Upstream: RPCEvent() {
//        @Serializable
//        sealed class SingleCall: Upstream() {
//            @Serializable
//            class Request(val serviceCall: ServiceCallIdentifier): SingleCall()
//        }
//        @Serializable
//        sealed class OutStream: Upstream() {
//            @Serializable
//            class Open(val serviceCall: ServiceCallIdentifier): OutStream()
//
//            @Serializable
//            object SendUpstream: OutStream()
//
//            @Serializable
//            object Close: OutStream()
//        }
//        @Serializable
//        sealed class InStream: Upstream() {
//            @Serializable
//            class Open(val serviceCall: ServiceCallIdentifier): InStream()
//        }
//        @Serializable
//        sealed class BiStream {
//            @Serializable
//            object Open: BiStream()
//
//            @Serializable
//            object SendUpstream: BiStream()
//
//            @Serializable
//            object Close: BiStream()
//        }
//    }
//
//    @Serializable
//    sealed class Downstream: RPCEvent() {
//        @Serializable
//        sealed class SingleCall: Downstream() {
//            @Serializable
//            object Response: SingleCall()
//
//            @Serializable
//            object Error: SingleCall()
//        }
//        @Serializable
//        sealed class OutStream: Downstream() {
//            @Serializable
//            object Response: OutStream()
//
//            @Serializable
//            object Error: OutStream()
//        }
//        @Serializable
//        sealed class InStream: Downstream() {
//            @Serializable
//            object SendDownstream: InStream()
//
//            @Serializable
//            object Close: InStream()
//        }
//        @Serializable
//        sealed class BiStream: Downstream() {
//            @Serializable
//            object SendDownstream: BiStream()
//
//            @Serializable
//            object Close: BiStream()
//        }
//    }
//}