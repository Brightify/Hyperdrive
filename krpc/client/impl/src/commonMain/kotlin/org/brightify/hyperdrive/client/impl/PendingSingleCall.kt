package org.brightify.hyperdrive.client.impl

import kotlinx.serialization.DeserializationStrategy
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

abstract class PendingSingleCall<INCOMING>(
    override val deserializationStrategy: DeserializationStrategy<INCOMING>,
    override val errorSerializer: RPCErrorSerializer,
): PendingRPC<INCOMING> {
    override val stateManager = PendingRPC.StateManager()
}