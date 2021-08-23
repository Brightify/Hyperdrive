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

/**
 * A synchronous property delegate accessor.
 *
 * NOTE: [DeferredObservableProperty] is the asynchronous variation lacking initial value.
 */
public interface ObservableProperty<T> {
    /**
     * Currently assigned value.
     */
    public val value: T

    /**
     * Add a [ValueChangeListener] for value change on this property.
     *
     * @return Cancellation token to cancel the listening.
     * Alternatively you can call [removeListener] with the same listener object as passed into this method.
     */
    public fun addListener(listener: ValueChangeListener<T>): CancellationToken

    /**
     * Remove a [ValueChangeListener] from listeners to value change on this property.
     *
     * @return Whether the listener was present at the time of removal.
     */
    public fun removeListener(listener: ValueChangeListener<T>): Boolean

    /**
     * Implemented by listeners to [ObservableProperty] value changes.
     */
    public interface ValueChangeListener<T> {
        /**
         * Listener method called before [ObservableProperty] value changes.
         *
         * @param oldValue current value
         * @param newValue next value
         */
        public fun valueWillChange(oldValue: T, newValue: T) { }

        /**
         * Listener method called after [ObservableProperty] value changes.
         *
         * @param oldValue previous value
         * @param newValue current value
         */
        public fun valueDidChange(oldValue: T, newValue: T) { }
    }

    /**
     * This interface is used to provide custom equality for various [ObservableProperty] operators
     * like [ObservableProperty.map] and [ObservableProperty.filter].
     *
     * Its purpose is to weed out duplicate values to prevent unnecessary UI updates.
     */
    public fun interface EqualityPolicy<T> {
        /**
         * Method for providing custom equality for [oldValue] and [newValue].
         */
        public fun isEqual(oldValue: T, newValue: T): Boolean
    }
}

/**
 * Block current thread in wait for the next value change.
 */
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

/**
 * Conversion method to [Channel].
 */
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

/**
 * Conversion method to [Flow].
 */
public fun <T> ObservableProperty<T>.asFlow(): Flow<T> = flow {
    emitAll(asChannel())
}
