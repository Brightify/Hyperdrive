package org.brightify.hyperdrive.krpc.protocol

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.krpc.description.ColdBistreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdDownstreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription
import org.brightify.hyperdrive.krpc.description.SingleCallDescription

public interface RPCOutgoingInterceptor {
    public suspend fun <PAYLOAD, RESPONSE> interceptOutgoingSingleCall(
        payload: PAYLOAD,
        call: SingleCallDescription<PAYLOAD, RESPONSE>,
        next: suspend (PAYLOAD) -> RESPONSE,
    ): RESPONSE {
        return next(payload)
    }

    public suspend fun <PAYLOAD, CLIENT_STREAM, RESPONSE> interceptOutgoingUpstreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: ColdUpstreamCallDescription<PAYLOAD, CLIENT_STREAM, RESPONSE>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> RESPONSE,
    ): RESPONSE {
        return next(payload, stream)
    }

    public suspend fun <PAYLOAD, SERVER_STREAM> interceptOutgoingDownstreamCall(
        payload: PAYLOAD,
        call: ColdDownstreamCallDescription<PAYLOAD, SERVER_STREAM>,
        next: suspend (PAYLOAD) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> {
        return next(payload)
    }

    public suspend fun <PAYLOAD, CLIENT_STREAM, SERVER_STREAM> interceptOutgoingBistreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: ColdBistreamCallDescription<PAYLOAD, CLIENT_STREAM, SERVER_STREAM>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> {
        return next(payload, stream)
    }
}