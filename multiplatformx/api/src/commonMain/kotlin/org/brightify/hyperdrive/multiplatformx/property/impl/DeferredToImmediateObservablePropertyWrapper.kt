package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.DeferredObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.utils.Optional
import org.brightify.hyperdrive.utils.someOrDefault

internal class DeferredToImmediateObservablePropertyWrapper<T>(
    private val initialValue: T,
    private val wrapped: DeferredObservableProperty<T>,
): ObservableProperty<T>, DeferredObservableProperty.Listener<T> {
    private val listeners = ValueChangeListenerHandler(this)

    override var value: T = initialValue
        private set

    private var pendingValue: Optional<T> = Optional.None

    init {
        wrapped.addListener(this)
    }

    override fun valueWillChange(oldValue: Optional<T>, newValue: T) {
        pendingValue = Optional.Some(newValue)
        listeners.notifyValueWillChange(value, newValue)
    }

    override fun valueDidChange(oldValue: Optional<T>, newValue: T) = pendingValue.withValue {
        value = it
        pendingValue = Optional.None
        listeners.notifyValueDidChange(oldValue.someOrDefault { initialValue }, value)
    }

    override fun addListener(listener: ObservableProperty.Listener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<T>): Boolean = listeners.removeListener(listener)
}
