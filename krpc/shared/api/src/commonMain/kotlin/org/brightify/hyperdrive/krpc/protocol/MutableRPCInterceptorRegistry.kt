package org.brightify.hyperdrive.krpc.protocol

public interface MutableRPCInterceptorRegistry: RPCInterceptorRegistry {
    public fun registerIncomingInterceptor(interceptor: RPCIncomingInterceptor)

    public fun registerOutgoingInterceptor(interceptor: RPCOutgoingInterceptor)

    public fun registerInterceptor(interceptor: RPCInterceptor)
}