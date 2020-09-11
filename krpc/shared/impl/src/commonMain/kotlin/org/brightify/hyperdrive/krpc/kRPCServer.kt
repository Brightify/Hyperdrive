package org.brightify.hyperdrive.krpc

import org.brightify.hyperdrive.krpc.api.Service

interface kRPCServiceClient {
    suspend fun <REQUEST, RESPONSE> unaryCall(request: REQUEST): RESPONSE
}

interface kRPCCall<IN, OUT> {

}
