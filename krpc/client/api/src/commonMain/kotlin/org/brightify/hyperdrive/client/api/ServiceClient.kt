package org.brightify.hyperdrive.client.api

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.ClientCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdBistreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdDownstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdUpstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ServiceCallIdentifier

interface ServiceClient {

    suspend fun <REQUEST, RESPONSE> singleCall(serviceCall: ClientCallDescriptor<REQUEST, RESPONSE>, request: REQUEST): RESPONSE

    suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> clientStream(serviceCall: ColdUpstreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>, request: REQUEST, clientStream: Flow<CLIENT_STREAM>): RESPONSE

    suspend fun <REQUEST, RESPONSE> serverStream(serviceCall: ColdDownstreamCallDescriptor<REQUEST, RESPONSE>, request: REQUEST): Flow<RESPONSE>

    suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> biStream(serviceCall: ColdBistreamCallDescriptor<REQUEST, CLIENT_STREAM, RESPONSE>, request: REQUEST, clientStream: Flow<CLIENT_STREAM>): Flow<RESPONSE>

    fun shutdown()

}
