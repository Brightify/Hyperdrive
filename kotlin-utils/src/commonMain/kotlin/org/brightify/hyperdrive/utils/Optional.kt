package org.brightify.hyperdrive.utils

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf

public sealed interface Optional<out T> {
    public data class Some<T>(public val value: T): Optional<T>

    public object None: Optional<Nothing>

    public fun withValue(block: (T) -> Unit) {
        if (this is Some) {
            block(value)
        }
    }
}

@OptIn(FlowPreview::class)
public fun <T> Flow<Optional<T>>.filterSome(): Flow<T> {
    return flatMapConcat {
        when (it) {
            is Optional.Some -> flowOf(it.value)
            Optional.None -> emptyFlow()
        }
    }
}

public inline fun <T> Optional<T>.someOrDefault(block: () -> T): T {
    return when (this) {
        is Optional.Some -> value
        Optional.None -> block()
    }
}
