package org.brightify.hyperdrive.krpc.protocol

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.description.ColdBistreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdDownstreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription
import org.brightify.hyperdrive.krpc.description.SingleCallDescription

class InterceptorEnabledRPCTransport(
    val transport: RPCTransport,
    val interceptor: RPCOutgoingInterceptor,
): RPCTransport {
    override suspend fun <REQUEST, RESPONSE> singleCall(serviceCall: SingleCallDescription<REQUEST, RESPONSE>, request: REQUEST): RESPONSE =
        interceptor.interceptOutgoingSingleCall(request, serviceCall) { newRequest ->
            transport.singleCall(serviceCall, newRequest)
        }

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> clientStream(
        serviceCall: ColdUpstreamCallDescription<REQUEST, CLIENT_STREAM, RESPONSE>,
        request: REQUEST,
        clientStream: Flow<CLIENT_STREAM>,
    ): RESPONSE = interceptor.interceptOutgoingUpstreamCall(
        request,
        clientStream,
        serviceCall,
    ) { newRequest, newClientStream ->
        transport.clientStream(serviceCall, newRequest, newClientStream)
    }

    override suspend fun <REQUEST, RESPONSE> serverStream(
        serviceCall: ColdDownstreamCallDescription<REQUEST, RESPONSE>,
        request: REQUEST,
    ): Flow<RESPONSE> = interceptor.interceptOutgoingDownstreamCall(request, serviceCall) { newRequest ->
        transport.serverStream(serviceCall, newRequest)
    }

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> biStream(
        serviceCall: ColdBistreamCallDescription<REQUEST, CLIENT_STREAM, RESPONSE>,
        request: REQUEST,
        clientStream: Flow<CLIENT_STREAM>,
    ): Flow<RESPONSE> = interceptor.interceptOutgoingBistreamCall(request, clientStream, serviceCall) { newRequest, newClientStream ->
        transport.biStream(serviceCall, newRequest, newClientStream)
    }

    override suspend fun close() {
        transport.close()
    }
}