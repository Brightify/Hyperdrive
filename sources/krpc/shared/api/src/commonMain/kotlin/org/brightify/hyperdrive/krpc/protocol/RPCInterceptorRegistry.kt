package org.brightify.hyperdrive.krpc.protocol

public interface RPCInterceptorRegistry {
    public fun combinedIncomingInterceptor(): RPCIncomingInterceptor

    public fun combinedOutgoingInterceptor(): RPCOutgoingInterceptor
}