package org.brightify.hyperdrive.multiplatformx.property

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.utils.Optional

/**
 * This interface is an asynchronous variation to the [ObservableProperty]. Used if the initial value is undefined.
 * It doesn't provide a value if it hasn't been assigned yet, but you can block the thread until it is.
 */
public interface DeferredObservableProperty<T> {
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
    public fun removeListener(listener: Listener<T>): Boolean

    /**
     * Returns the latest value if any was emitted, `null` otherwise.
     */
    public val latestValue: Optional<T>

    /**
     * Blocks the current thread to wait for a value if [latestValue] is `null`,
     * otherwise returns [latestValue] immediately.
     *
     * Behaves identically to [nextValue] before the first value is assigned.
     */
    public suspend fun await(): T

    /**
     * Blocks the current thread until [latestValue] is replaced by a new value.
     *
     * Behaves identically to [await] before the first value is assigned.
     */
    public suspend fun nextValue(): T

    /**
     * Implemented by listeners to [DeferredObservableProperty] value changes.
     */
    public interface Listener<T>: ValueChangeListener<Optional<T>, T>

    public companion object {
        public fun <T> valueWillChange(block: Listener<T>.(oldValue: Optional<T>, newValue: T) -> Unit): Listener<T> = object: Listener<T> {
            override fun valueWillChange(oldValue: Optional<T>, newValue: T) {
                block(oldValue, newValue)
            }
        }

        public fun <T> valueDidChange(block: Listener<T>.(oldValue: Optional<T>, newValue: T) -> Unit): Listener<T> = object: Listener<T> {
            override fun valueDidChange(oldValue: Optional<T>, newValue: T) {
                block(oldValue, newValue)
            }
        }
    }
}
