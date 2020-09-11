package org.brightify.hyperdrive.krpc

// Intentionally not marked as @Service as we need to implement client and server in separate modules.
interface PingService {

    suspend fun ping()

}