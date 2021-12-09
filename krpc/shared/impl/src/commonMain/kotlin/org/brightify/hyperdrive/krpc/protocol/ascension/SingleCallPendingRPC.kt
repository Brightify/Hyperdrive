package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.frame.AscensionRPCFrame
import org.brightify.hyperdrive.krpc.protocol.RPC
import org.brightify.hyperdrive.krpc.util.RPCReference
import kotlin.coroutines.CoroutineContext

public object SingleCallPendingRPC {
    public class Callee(
        protocol: AscensionRPCProtocol,
        context: CoroutineContext,
        reference: RPCReference,
        private val implementation: RPC.SingleCall.Callee.Implementation
    ): PendingRPC.Callee<AscensionRPCFrame.SingleCall.Upstream, AscensionRPCFrame.SingleCall.Downstream>(protocol, context, reference, logger), RPC.SingleCall.Callee {
        private companion object {
            val logger = Logger<SingleCallPendingRPC.Callee>()
        }

        private sealed interface State {
            object Created: State
            object Completed: State
        }

        private val state = MutableStateFlow<State>(State.Created)

        override val shouldComplete: Flow<Boolean> = state.map { it == State.Completed }

        override suspend fun handle(frame: AscensionRPCFrame.SingleCall.Upstream) {
            when (frame) {
                is AscensionRPCFrame.SingleCall.Upstream.Open -> {
                    val response = implementation.perform(frame.payload)
                    send(AscensionRPCFrame.SingleCall.Downstream.Response(response, reference))
                    state.value = State.Completed
                }
            }
        }
    }

    public class Caller(
        protocol: AscensionRPCProtocol,
        context: CoroutineContext,
        private val serviceCallIdentifier: ServiceCallIdentifier,
        reference: RPCReference,
    ): PendingRPC.Caller<AscensionRPCFrame.SingleCall.Downstream, AscensionRPCFrame.SingleCall.Upstream>(protocol, context, reference, logger), RPC.SingleCall.Caller {
        private companion object {
            val logger = Logger<SingleCallPendingRPC.Caller>()
        }

        private val responseDeferred = CompletableDeferred<SerializedPayload>(coroutineContext.job)

        override val shouldComplete: Flow<Boolean> = responseDeferred.isCompletedFlow

        override suspend fun perform(payload: SerializedPayload): SerializedPayload {
            send(AscensionRPCFrame.SingleCall.Upstream.Open(payload, serviceCallIdentifier, reference))

            return responseDeferred.await()
        }

        override suspend fun handle(frame: AscensionRPCFrame.SingleCall.Downstream) {
            when (frame) {
                is AscensionRPCFrame.SingleCall.Downstream.Response -> responseDeferred.complete(frame.payload)
            }
        }
    }
}
