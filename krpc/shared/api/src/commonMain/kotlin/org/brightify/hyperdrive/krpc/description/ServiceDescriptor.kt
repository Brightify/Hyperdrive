package org.brightify.hyperdrive.krpc.description

interface ServiceDescriptor<S> {
    fun describe(service: S): ServiceDescription
}

