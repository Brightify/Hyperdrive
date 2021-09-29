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

public inline fun <T: Any> Optional<T>.toKotlin(): T? {
    return when (this) {
        is Optional.Some -> value
        is Optional.None -> null
    }
}

public inline fun <T: Any> T?.toOptional(): Optional<T> {
    return this?.let { Optional.Some(it) } ?: Optional.None
}

public inline fun <T, U> Optional<T>.map(transform: (T) -> U): Optional<U> {
    return flatMap { Optional.Some(transform(it)) }
}

public inline fun <T, U: Any> Optional<T>.mapToKotlin(transform: (T) -> U): U? {
    return when (this) {
        is Optional.Some -> transform(value)
        is Optional.None -> null
    }
}

public inline fun <T, U> Optional<T>.flatMap(transform: (T) -> Optional<U>): Optional<U> {
    return when (this) {
        is Optional.Some -> transform(value)
        is Optional.None -> Optional.None
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
