package org.brightify.hyperdrive.krpc.api.impl

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import org.brightify.hyperdrive.krpc.api.ClientCallDescriptor
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

abstract class PendingSingleCall<OUTGOING, INCOMING>(
    final override val descriptor: ClientCallDescriptor<OUTGOING, INCOMING>,
    override val payload: OUTGOING,
): PendingRPC<OUTGOING, INCOMING> {
    override val payloadSerializationStrategy: SerializationStrategy<OUTGOING> = descriptor.outgoingSerializer
    override val deserializationStrategy: DeserializationStrategy<INCOMING> = descriptor.incomingSerializer
    override val errorSerializer: RPCErrorSerializer = descriptor.errorSerializer
    override val stateManager = PendingRPC.StateManager()
}