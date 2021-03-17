package org.brightify.hyperdrive.krpc.api.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import org.brightify.hyperdrive.krpc.api.ColdUpstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

abstract class OpenOutStream<OUTGOING, OUTGOING_STREAM, INCOMING>(
    final override val descriptor: ColdUpstreamCallDescriptor<OUTGOING, OUTGOING_STREAM, INCOMING>,
    override val payload: OUTGOING,
    val stream: Flow<OUTGOING_STREAM>,
): PendingRPC<OUTGOING, INCOMING> {
    override val payloadSerializationStrategy: SerializationStrategy<OUTGOING> = descriptor.outgoingSerializer
    val serializationStrategy: SerializationStrategy<OUTGOING_STREAM> = descriptor.clientStreamSerializer
    override val deserializationStrategy: DeserializationStrategy<INCOMING> = descriptor.incomingSerializer
    override val errorSerializer: RPCErrorSerializer = descriptor.errorSerializer
    override val stateManager = PendingRPC.StateManager()
}