package org.brightify.hyperdrive.krpc.error

import kotlinx.coroutines.CancellationException

public expect class ConnectionClosedException: CancellationException

public expect fun ConnectionClosedException(message: String = "Connection has been closed", cause: Throwable? = null): ConnectionClosedException