package org.brightify.hyperdrive.krpc.api

interface ServiceDescriptor<S> {
    fun describe(service: S): ServiceDescription
}

