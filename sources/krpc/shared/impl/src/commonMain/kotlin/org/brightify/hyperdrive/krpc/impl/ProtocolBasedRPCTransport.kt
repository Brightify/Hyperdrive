package org.brightify.hyperdrive.krpc.impl

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.description.ColdBistreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdDownstreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.protocol.RPCProtocol
import org.brightify.hyperdrive.krpc.application.runner.ColdBistreamRunner
import org.brightify.hyperdrive.krpc.application.runner.ColdDownstreamRunner
import org.brightify.hyperdrive.krpc.application.runner.ColdUpstreamRunner
import org.brightify.hyperdrive.krpc.application.PayloadSerializer
import org.brightify.hyperdrive.krpc.application.runner.SingleCallRunner

public class ProtocolBasedRPCTransport(
    public val protocol: RPCProtocol,
    public val payloadSerializer: PayloadSerializer,
): RPCTransport {
    override suspend fun <REQUEST, RESPONSE> singleCall(serviceCall: SingleCallDescription<REQUEST, RESPONSE>, request: REQUEST): RESPONSE {
        return SingleCallRunner.Caller(
            payloadSerializer,
            protocol.singleCall(serviceCall.identifier),
            serviceCall,
        ).run(request)
    }

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> clientStream(
        serviceCall: ColdUpstreamCallDescription<REQUEST, CLIENT_STREAM, RESPONSE>,
        request: REQUEST,
        clientStream: Flow<CLIENT_STREAM>,
    ): RESPONSE {
        return ColdUpstreamRunner.Caller(
            payloadSerializer,
            protocol.upstream(serviceCall.identifier),
            serviceCall,
        ).run(request, clientStream)
    }

    override suspend fun <REQUEST, RESPONSE> serverStream(
        serviceCall: ColdDownstreamCallDescription<REQUEST, RESPONSE>,
        request: REQUEST,
    ): Flow<RESPONSE> {
        return ColdDownstreamRunner.Caller(
            payloadSerializer,
            protocol.downstream(serviceCall.identifier),
            serviceCall,
        ).run(request)
    }

    override suspend fun <REQUEST, CLIENT_STREAM, RESPONSE> biStream(
        serviceCall: ColdBistreamCallDescription<REQUEST, CLIENT_STREAM, RESPONSE>,
        request: REQUEST,
        clientStream: Flow<CLIENT_STREAM>,
    ): Flow<RESPONSE> {
        return ColdBistreamRunner.Caller(
            payloadSerializer,
            protocol.bistream(serviceCall.identifier),
            serviceCall,
        ).run(request, clientStream)
    }
}