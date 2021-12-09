package org.brightify.hyperdrive.krpc.error;

import kotlinx.coroutines.CancellationException

public actual class ConnectionClosedException internal constructor(message: String): CancellationException(message)

private typealias ConnectionClosedExceptionWorkaround = ConnectionClosedException

public actual fun ConnectionClosedException(message: String, cause: Throwable?): ConnectionClosedException {
    return ConnectionClosedExceptionWorkaround(message).also {
        it.initCause(cause)
    }
}
