package org.brightify.hyperdrive.krpc.error

import kotlinx.coroutines.CancellationException

public actual class ConnectionClosedException internal constructor(message: String?, cause: Throwable?): CancellationException(message, cause)

private typealias ConnectionClosedExceptionWorkaround = ConnectionClosedException

public actual fun ConnectionClosedException(message: String, cause: Throwable?): ConnectionClosedException {
    return ConnectionClosedExceptionWorkaround(message, cause)
}
