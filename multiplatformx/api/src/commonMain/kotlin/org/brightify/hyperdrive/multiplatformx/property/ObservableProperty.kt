@file:Suppress("unused")

package org.brightify.hyperdrive.multiplatformx.property

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.Lifecycle
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

public typealias ViewModelProperty<T> = ObservableProperty<T>

public interface ObservableProperty<T> {
    public val value: T

    public fun addListener(listener: ValueChangeListener<T>): CancellationToken

    public fun removeListener(listener: ValueChangeListener<T>): Boolean

    public interface ValueChangeListener<T> {
        public fun valueWillChange(oldValue: T, newValue: T) { }

        public fun valueDidChange(oldValue: T, newValue: T) { }
    }

    public fun interface EqualityPolicy<T> {
        public fun isEqual(oldValue: T, newValue: T): Boolean
    }
}

public suspend fun <T> ObservableProperty<T>.nextValue(): T {
    val completable = CompletableDeferred<T>()
    val listener = object: ObservableProperty.ValueChangeListener<T> {
        override fun valueDidChange(oldValue: T, newValue: T) {
            completable.complete(oldValue)
            removeListener(this)
        }
    }
    addListener(listener)
    completable.invokeOnCompletion {
        removeListener(listener)
    }
    return completable.await()
}

internal fun <OWNER, T> ObservableProperty<T>.toKotlinProperty(): ReadOnlyProperty<OWNER, T> = ReadOnlyProperty { _, _ ->
    this@toKotlinProperty.value
}

internal fun <OWNER, T> MutableObservableProperty<T>.toKotlinMutableProperty(): ReadWriteProperty<OWNER, T> =
    object: ReadWriteProperty<OWNER, T> {
        override fun getValue(thisRef: OWNER, property: KProperty<*>): T = this@toKotlinMutableProperty.value

        override fun setValue(thisRef: OWNER, property: KProperty<*>, value: T) {
            this@toKotlinMutableProperty.value = value
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
public fun <T> ObservableProperty<T>.asChannel(): Channel<T> {
    val channel = Channel<T>(Channel.CONFLATED)
    val listener = object: ObservableProperty.ValueChangeListener<T> {
        override fun valueDidChange(oldValue: T, newValue: T) {
            channel.trySend(value)
        }
    }
    addListener(listener)
    channel.invokeOnClose {
        removeListener(listener)
    }
    return channel
}

public fun <T> ObservableProperty<T>.asFlow(): Flow<T> = flow {
    emitAll(asChannel())
}
