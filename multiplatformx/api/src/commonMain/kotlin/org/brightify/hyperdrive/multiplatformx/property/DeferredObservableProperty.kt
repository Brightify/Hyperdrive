package org.brightify.hyperdrive.multiplatformx.property

import org.brightify.hyperdrive.multiplatformx.CancellationToken

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
    public fun addListener(listener: ValueChangeListener<T>): CancellationToken

    /**
     * Remove a [ValueChangeListener] from listeners to value change on this property.
     *
     * @return Whether the listener was present at the time of removal.
     */
    public fun removeListener(listener: ValueChangeListener<T>): Boolean

    /**
     * Returns the latest value if any was emitted, `null` otherwise.
     */
    public val latestValue: T?

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
    public interface ValueChangeListener<T> {
        /**
         * Listener method called before [DeferredObservableProperty] value changes.
         *
         * @param oldValue current value
         * @param newValue next value
         */
        public fun valueWillChange(oldValue: T, newValue: T) { }

        /**
         * Listener method called after [DeferredObservableProperty] value changes.
         *
         * @param oldValue previous value
         * @param newValue current value
         */
        public fun valueDidChange(oldValue: T, newValue: T) { }
    }
}
