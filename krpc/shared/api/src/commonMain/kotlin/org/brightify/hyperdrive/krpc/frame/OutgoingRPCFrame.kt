package org.brightify.hyperdrive.krpc.frame

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy

data class OutgoingRPCFrame<out EVENT: RPCEvent>(
    override val header: RPCFrame.Header<EVENT>,
    val serializationStrategy: SerializationStrategy<Any?>,
    val data: @Contextual Any?
): RPCFrame<EVENT> {
    override fun toString(): String = "Outgoing(header=$header, data=$data)"

    companion object {
        operator fun <EVENT: RPCEvent, PAYLOAD> invoke(
            header: RPCFrame.Header<EVENT>,
            serializationStrategy: KSerializer<out PAYLOAD>,
            payload: PAYLOAD,
        ): OutgoingRPCFrame<EVENT> {
            return OutgoingRPCFrame(
                header,
                serializationStrategy as SerializationStrategy<Any?>,
                payload,
            )
        }
    }
}