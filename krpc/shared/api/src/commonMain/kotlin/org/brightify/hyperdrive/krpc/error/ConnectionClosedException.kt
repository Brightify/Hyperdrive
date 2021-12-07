package org.brightify.hyperdrive.krpc.error

import kotlinx.coroutines.CancellationException

public class ConnectionClosedException(mesage: String = "Connection has been closed"): CancellationException(mesage)
