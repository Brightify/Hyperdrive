package org.brightify.hyperdrive.multiplatformx.property

import org.brightify.hyperdrive.multiplatformx.CancellationToken

public interface DeferredObservableProperty<T> {
    public fun addListener(listener: ValueChangeListener<T>): CancellationToken

    public fun removeListener(listener: ValueChangeListener<T>): Boolean

    public val latestValue: T?

    public suspend fun await(): T

    public suspend fun nextValue(): T

    public interface ValueChangeListener<T> {
        public fun valueWillChange(oldValue: T?, newValue: T) { }

        public fun valueDidChange(oldValue: T?, newValue: T) { }
    }
}