package org.brightify.hyperdrive.krpc.api.impl

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import org.brightify.hyperdrive.krpc.api.ColdBistreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdDownstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.LocalInStreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

abstract class OpenInStream<OUTGOING, INCOMING>(
    override val descriptor: LocalInStreamCallDescriptor<OUTGOING>,
    override val payload: OUTGOING,
    override val payloadSerializationStrategy: SerializationStrategy<OUTGOING>,
    override val deserializationStrategy: DeserializationStrategy<INCOMING>,
    override val errorSerializer: RPCErrorSerializer,
): PendingRPC<OUTGOING, INCOMING> {
    constructor(descriptor: ColdDownstreamCallDescriptor<OUTGOING, INCOMING>, payload: OUTGOING):
        this(descriptor, payload, descriptor.outgoingSerializer, descriptor.serverStreamSerializer, descriptor.errorSerializer)

    constructor(descriptor: ColdBistreamCallDescriptor<OUTGOING, *, INCOMING>, payload: OUTGOING):
        this(descriptor, payload, descriptor.outgoingSerializer, descriptor.serverStreamSerializer, descriptor.errorSerializer)

    override val stateManager = PendingRPC.StateManager()
}