package org.brightify.hyperdrive.property.impl

import kotlinx.coroutines.coroutineScope
import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.property.DeferredObservableProperty
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.property.nextValue
import org.brightify.hyperdrive.utils.Optional

internal class ImmediateToDeferredObservablePropertyWrapper<T>(
    private val wrapped: ObservableProperty<T>,
): DeferredObservableProperty<T>, ObservableProperty.Listener<T> {
    private val listeners = ValueChangeListenerHandler(this)

    init {
        wrapped.addListener(this)
    }

    override fun valueWillChange(oldValue: T, newValue: T) {
        listeners.notifyValueWillChange(Optional.Some(oldValue), newValue)
    }

    override fun valueDidChange(oldValue: T, newValue: T) {
        listeners.notifyValueDidChange(Optional.Some(oldValue), newValue)
    }

    override fun addListener(listener: DeferredObservableProperty.Listener<T>): CancellationToken =
        listeners.addListener(listener)

    override fun removeListener(listener: DeferredObservableProperty.Listener<T>) =
        listeners.removeListener(listener)

    override val latestValue: Optional<T>
        get() = Optional.Some(wrapped.value)

    override suspend fun await(): T = coroutineScope {
        wrapped.value
    }

    override suspend fun nextValue(): T = coroutineScope {
        wrapped.nextValue()
    }
}
