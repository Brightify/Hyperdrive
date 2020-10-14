package org.brightify.hyperdrive.krpc.server.impl

import org.brightify.hyperdrive.krpc.PingService
import org.brightify.hyperdrive.krpc.api.ServiceDescription
import org.brightify.hyperdrive.krpc.api.ServiceDescriptor
import javax.annotation.processing.Generated

@Generated
object PingServiceDescriptor: ServiceDescriptor<PingService> {
    override fun describe(service: PingService): ServiceDescription {
        return ServiceDescription("PingService", emptyList())
    }
}