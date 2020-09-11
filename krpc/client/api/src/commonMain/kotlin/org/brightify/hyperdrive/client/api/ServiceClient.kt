package org.brightify.hyperdrive.client.api

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.ClientCallDescriptor
import org.brightify.hyperdrive.krpc.api.ServiceCallIdentifier

interface ServiceClient {

    suspend fun <REQUEST, RESPONSE> singleCall(serviceCall: ClientCallDescriptor<REQUEST, RESPONSE>, request: REQUEST): RESPONSE

    suspend fun <REQUEST, RESPONSE> clientStream(descriptor: CallDescriptor, request: Flow<REQUEST>): RESPONSE

    suspend fun <REQUEST, RESPONSE> serverStream(descriptor: CallDescriptor, request: REQUEST): Flow<RESPONSE>

    suspend fun <REQUEST, RESPONSE> bidiStream(descriptor: CallDescriptor, request: Flow<REQUEST>): Flow<RESPONSE>

    fun shutdown()

}
