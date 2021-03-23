package org.brightify.hyperdrive.krpc.server.api

import org.brightify.hyperdrive.krpc.description.ServiceDescription

interface Server {
    fun register(description: ServiceDescription)
}