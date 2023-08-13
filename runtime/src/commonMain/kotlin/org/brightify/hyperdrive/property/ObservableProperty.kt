@file:Suppress("unused")

package org.brightify.hyperdrive.property

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.brightify.hyperdrive.CancellationToken
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
    public fun addListener(listener: Listener<T>): CancellationToken

    /**
     * Remove a [ValueChangeListener] from listeners to value change on this property.
     *
     * @return Whether the listener was present at the time of removal.
     */
    public fun removeListener(listener: Listener<T>)

    /**
     * Implemented by listeners to [ObservableProperty] value changes.
     */
    public interface Listener<T>: ValueChangeListener<T, T>

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

    public companion object {
        public fun <T> valueWillChange(block: Listener<T>.(oldValue: T, newValue: T) -> Unit): Listener<T> = object: Listener<T> {
            override fun valueWillChange(oldValue: T, newValue: T) {
                block(oldValue, newValue)
            }
        }

        public fun <T> valueDidChange(block: Listener<T>.(oldValue: T, newValue: T) -> Unit): Listener<T> = object: Listener<T> {
            override fun valueDidChange(oldValue: T, newValue: T) {
                block(oldValue, newValue)
            }
        }
    }
}

/**
 * Block current thread in wait for the next value change.
 */
public suspend fun <T> ObservableProperty<T>.nextValue(): T {
    val completable = CompletableDeferred<T>()
    val listener = ObservableProperty.valueDidChange<T> { _, newValue ->
        completable.complete(newValue)
        removeListener(this)
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

    val listener = ObservableProperty.valueDidChange<T> { _, newValue ->
        channel.trySend(newValue)
    }

    // TODO: If the value changes between the next two lines, it won't get delivered since the listener is not added yet.
    channel.trySend(value)
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
