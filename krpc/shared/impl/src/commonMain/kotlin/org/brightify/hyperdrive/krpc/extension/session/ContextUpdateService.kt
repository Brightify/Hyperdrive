package org.brightify.hyperdrive.krpc.extension.session

import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import org.brightify.hyperdrive.krpc.description.ServiceDescriptor
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer

public interface ContextUpdateService {
    public suspend fun update(request: ContextUpdateRequest): ContextUpdateResult

    public suspend fun clear()

    public class Client(
        private val transport: RPCTransport,
    ): ContextUpdateService {
        override suspend fun update(request: ContextUpdateRequest): ContextUpdateResult {
            return transport.singleCall(Descriptor.Call.update, request)
        }

        override suspend fun clear() {
            return transport.singleCall(Descriptor.Call.clear, Unit)
        }
    }

    public object Descriptor: ServiceDescriptor<ContextUpdateService> {
        public const val identifier: String = "builtin:hyperdrive.ContextSyncService"

        override fun describe(service: ContextUpdateService): ServiceDescription {
            return ServiceDescription(
                identifier,
                listOf(
                    Call.update.calling { request ->
                        service.update(request)
                    },
                    Call.clear.calling {
                        service.clear()
                    }
                )
            )
        }

        public object Call {
            public val update: SingleCallDescription<ContextUpdateRequest, ContextUpdateResult> = SingleCallDescription(
                ServiceCallIdentifier(identifier, "update"),
                ContextUpdateRequest.serializer(),
                ContextUpdateResult.serializer(),
                RPCErrorSerializer(),
            )

            public val clear: SingleCallDescription<Unit, Unit> = SingleCallDescription(
                ServiceCallIdentifier(identifier, "clear"),
                Unit.serializer(),
                Unit.serializer(),
                RPCErrorSerializer(),
            )
        }
    }
}