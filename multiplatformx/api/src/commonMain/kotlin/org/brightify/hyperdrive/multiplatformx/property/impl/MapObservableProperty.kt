package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

internal class MapObservableProperty<T, U>(
    private val mapped: ObservableProperty<T>,
    private val transform: (T) -> U,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<U>,
): ObservableProperty<U>, ObservableProperty.ValueChangeListener<T> {
    override var value: U = transform(mapped.value)
        private set
    private var pendingValue: U? = null

    private val listeners = ObservablePropertyListeners(this)

    init {
        mapped.addListener(this)
    }

    override fun valueWillChange(oldValue: T, newValue: T) {
        val newTransformedValue = transform(newValue)
        if (equalityPolicy.isEqual(value, newTransformedValue)) { return }
        pendingValue = newTransformedValue
        listeners.notifyValueWillChange(value, newTransformedValue)
    }

    override fun valueDidChange(oldValue: T, newValue: T) {
        val oldTransformedValue = value
        value = pendingValue ?: return
        pendingValue = null
        listeners.notifyValueDidChange(oldTransformedValue, value)
    }

    override fun addListener(listener: ObservableProperty.ValueChangeListener<U>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.ValueChangeListener<U>): Boolean = listeners.removeListener(listener)
}

