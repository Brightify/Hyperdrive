package org.brightify.hyperdrive.krpc.server.impl

import org.brightify.hyperdrive.krpc.PingService
import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.api.ServiceDescription
import org.brightify.hyperdrive.krpc.api.ServiceDescriptor
import org.brightify.hyperdrive.krpc.server.api.Server
import javax.annotation.processing.Generated

class PingServiceImpl: PingService {
    override suspend fun ping() {

    }
}

@Generated
object PingServiceDescriptor: ServiceDescriptor<PingService> {
    override fun describe(service: PingService): ServiceDescription {
        return ServiceDescription("PingService", emptyList())
    }
}

@Generated
internal fun Server.register(service: PingService) {
    this.register(PingServiceDescriptor.describe(service))
}