package org.brightify.hyperdrive.client.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

abstract class OpenOutStream<OUTGOING, INCOMING>(
    val serializationStrategy: SerializationStrategy<OUTGOING>,
    override val deserializationStrategy: DeserializationStrategy<INCOMING>,
    override val errorSerializer: RPCErrorSerializer,
    val stream: Flow<OUTGOING>,
): PendingRPC<INCOMING> {
    override val stateManager = PendingRPC.StateManager()
}