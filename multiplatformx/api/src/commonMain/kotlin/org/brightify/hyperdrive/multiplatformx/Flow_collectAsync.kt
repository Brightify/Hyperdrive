package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

public fun <T> Flow<T>.collectAsync(handler: (T) -> Unit): CancellationToken {
    val job = MultiplatformGlobalScope.launch {
        this@collectAsync.collect {
            handler(it)
        }
    }
    return CancellationToken {
        job.cancel()
    }
}