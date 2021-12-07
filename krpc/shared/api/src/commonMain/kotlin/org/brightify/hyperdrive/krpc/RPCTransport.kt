package org.brightify.hyperdrive.krpc

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.description.ColdBistreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdDownstreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription

public interface RPCTransport {
    public suspend fun <REQUEST, RESPONSE> singleCall(
        serviceCall: SingleCallDescription<REQUEST, RESPONSE>,
        request: REQUEST
    ): RESPONSE

    public suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> clientStream(
        serviceCall: ColdUpstreamCallDescription<REQUEST, CLIENT_STREAM, RESPONSE>,
        request: REQUEST,
        clientStream: Flow<CLIENT_STREAM>
    ): RESPONSE

    public suspend fun <REQUEST, RESPONSE> serverStream(
        serviceCall: ColdDownstreamCallDescription<REQUEST, RESPONSE>,
        request: REQUEST
    ): Flow<RESPONSE>

    public suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> biStream(
        serviceCall: ColdBistreamCallDescription<REQUEST, CLIENT_STREAM, RESPONSE>,
        request: REQUEST,
        clientStream: Flow<CLIENT_STREAM>
    ): Flow<RESPONSE>
}