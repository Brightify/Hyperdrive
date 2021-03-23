package org.brightify.hyperdrive.krpc.api

import kotlinx.coroutines.flow.Flow

interface RPCTransport {
    suspend fun <REQUEST, RESPONSE> singleCall(
        serviceCall: ClientCallDescriptor<REQUEST, RESPONSE>,
        request: REQUEST
    ): RESPONSE

    suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> clientStream(
        serviceCall: ColdUpstreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>,
        request: REQUEST,
        clientStream: Flow<CLIENT_STREAM>
    ): RESPONSE

    suspend fun <REQUEST, RESPONSE> serverStream(
        serviceCall: ColdDownstreamCallDescriptor<REQUEST, RESPONSE>,
        request: REQUEST
    ): Flow<RESPONSE>

    suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> biStream(
        serviceCall: ColdBistreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>,
        request: REQUEST,
        clientStream: Flow<CLIENT_STREAM>
    ): Flow<RESPONSE>

    suspend fun close()
}