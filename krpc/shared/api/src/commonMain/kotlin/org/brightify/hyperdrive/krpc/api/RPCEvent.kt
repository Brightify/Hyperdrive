package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.Contextual
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

typealias RPCFrameIndex = Int

@Serializable
data class ServiceCallIdentifier(
    val serviceId: String,
    val callId: String
)

sealed class ClientRPC {
    class SingleRPC<REQUEST, RESPONSE>: ClientRPC() {

    }

    class OutStream<REQUEST, OUT_FLOW, RESPONSE>: ClientRPC() {

    }

    class InStream<REQUEST, IN_FLOW>: ClientRPC() {

    }

    class BiStream<REQUEST, OUT_FLOW, IN_FLOW>: ClientRPC() {

    }
}



@Serializable
sealed class RPCEvent {
    @Serializable
    sealed class Upstream: RPCEvent() {
        @Serializable
        sealed class SingleCall: Upstream() {
            @Serializable
            class Request(val serviceCall: ServiceCallIdentifier): SingleCall()
        }
        @Serializable
        sealed class OutStream: Upstream() {
            @Serializable
            class Open(val serviceCall: ServiceCallIdentifier): OutStream()
            @Serializable
            class SendUpstream: OutStream()
            @Serializable
            class Close(val error: @Contextual Throwable?): OutStream()
        }
        @Serializable
        sealed class InStream: Upstream() {
            @Serializable
            class Open: InStream()
        }
        @Serializable
        sealed class BiStream {
            @Serializable
            class Open: BiStream()
            @Serializable
            class SendUpstream: BiStream()
            @Serializable
            class Close(val error: @Contextual Throwable?): BiStream()
        }
    }

    @Serializable
    sealed class Downstream: RPCEvent() {
        @Serializable
        sealed class SingleCall: Downstream() {
            @Serializable
            class Response(val requestIndex: RPCFrameIndex): SingleCall()
            @Serializable
            class Error(val requestIndex: RPCFrameIndex): SingleCall()
        }
        @Serializable
        sealed class ClientStream: Downstream() {
            @Serializable
            class Response(): ClientStream()
            @Serializable
            class Error(val result: @Contextual Throwable): ClientStream()
        }
        @Serializable
        sealed class ServerStream: Downstream() {
            @Serializable
            class SendDownstream: ServerStream()
            @Serializable
            class Close(val error: @Contextual Throwable?): ServerStream()
        }
        @Serializable
        sealed class BidiStream: Downstream() {
            @Serializable
            class SendDownstream: BidiStream()
            @Serializable
            class Close(val error: @Contextual Throwable?): BidiStream()
        }
    }
}