package org.brightify.hyperdrive.multiplatformx.util.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.MultiplatformGlobalScope

@Deprecated("collectAsync uses MultiplatformGlobalScope which should never be used. Use lifecycle instead!", level = DeprecationLevel.ERROR)
public fun <T> Flow<T>.collectAsync(handler: (T) -> Unit): CancellationToken {
    @Suppress("DEPRECATION")
    val job = MultiplatformGlobalScope.launch {
        this@collectAsync.collect {
            handler(it)
        }
    }
    return CancellationToken {
        job.cancel()
    }
}