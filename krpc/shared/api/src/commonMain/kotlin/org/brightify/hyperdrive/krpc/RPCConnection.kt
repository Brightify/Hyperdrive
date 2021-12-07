package org.brightify.hyperdrive.krpc

import kotlinx.coroutines.CoroutineScope

public interface RPCConnection: CoroutineScope {
    public suspend fun close()

    public suspend fun receive(): SerializedFrame

    public suspend fun send(frame: SerializedFrame)
}

