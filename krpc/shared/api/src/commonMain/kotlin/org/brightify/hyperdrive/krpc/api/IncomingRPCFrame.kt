package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.encoding.Decoder

data class IncomingRPCFrame<out EVENT: RPCEvent>(
    override val header: RPCFrame.Header<EVENT>,
    val decoder: Decoder
): RPCFrame<EVENT>