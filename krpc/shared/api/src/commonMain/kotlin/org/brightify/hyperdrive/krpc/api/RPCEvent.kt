package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.Serializable

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
