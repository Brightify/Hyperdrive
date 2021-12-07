package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.frame.AscensionRPCFrame
import org.brightify.hyperdrive.krpc.protocol.RPC
import org.brightify.hyperdrive.krpc.util.RPCReference

public object SingleCallPendingRPC {
    public class Callee(
        protocol: AscensionRPCProtocol,
        scope: CoroutineScope,
        reference: RPCReference,
        private val implementation: RPC.SingleCall.Callee.Implementation
    ): PendingRPC.Callee<AscensionRPCFrame.SingleCall.Upstream, AscensionRPCFrame.SingleCall.Downstream>(protocol, scope, reference, logger), RPC.SingleCall.Callee {
        private companion object {
            val logger = Logger<SingleCallPendingRPC.Callee>()
        }

        override suspend fun handle(frame: AscensionRPCFrame.SingleCall.Upstream): Unit = completionTracker.tracking {
            when (frame) {
                is AscensionRPCFrame.SingleCall.Upstream.Open -> {
                    val response = implementation.perform(frame.payload)
                    send(AscensionRPCFrame.SingleCall.Downstream.Response(response, reference))
                }
            }
        }
    }

    public class Caller(
        protocol: AscensionRPCProtocol,
        scope: CoroutineScope,
        private val serviceCallIdentifier: ServiceCallIdentifier,
        reference: RPCReference,
    ): PendingRPC.Caller<AscensionRPCFrame.SingleCall.Downstream, AscensionRPCFrame.SingleCall.Upstream>(protocol, scope, reference, logger), RPC.SingleCall.Caller {
        private companion object {
            val logger = Logger<SingleCallPendingRPC.Caller>()
        }

        private val responseDeferred = CompletableDeferred<SerializedPayload>(coroutineContext.job)

        override suspend fun perform(payload: SerializedPayload): SerializedPayload = completionTracker.tracking {
            send(AscensionRPCFrame.SingleCall.Upstream.Open(payload, serviceCallIdentifier, reference))

            responseDeferred.await()
        }

        override suspend fun handle(frame: AscensionRPCFrame.SingleCall.Downstream) {
            when (frame) {
                is AscensionRPCFrame.SingleCall.Downstream.Response -> responseDeferred.complete(frame.payload)
            }
        }
    }
}
