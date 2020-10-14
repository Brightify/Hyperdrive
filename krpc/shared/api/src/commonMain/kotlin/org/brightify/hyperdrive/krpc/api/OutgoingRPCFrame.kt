package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerializationStrategy

data class OutgoingRPCFrame<out EVENT: RPCEvent>(
    override val header: RPCFrame.Header<EVENT>,
    val serializationStrategy: SerializationStrategy<Any?>,
    val data: @Contextual Any?
): RPCFrame<EVENT>