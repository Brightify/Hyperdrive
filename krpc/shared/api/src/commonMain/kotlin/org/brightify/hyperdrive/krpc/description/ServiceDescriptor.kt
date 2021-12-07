package org.brightify.hyperdrive.krpc.description

public interface ServiceDescriptor<S> {
    public fun describe(service: S): ServiceDescription
}

