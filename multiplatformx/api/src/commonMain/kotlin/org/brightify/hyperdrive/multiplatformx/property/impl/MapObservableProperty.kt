package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.utils.Optional

internal class MapObservableProperty<T, U>(
    private val mapped: ObservableProperty<T>,
    private val transform: (T) -> U,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<U>,
): ObservableProperty<U>, ObservableProperty.Listener<T> {
    override var value: U = transform(mapped.value)
        private set
    private var pendingValue: Optional<U> = Optional.None

    private val listeners = ValueChangeListenerHandler(this)

    init {
        mapped.addListener(this)
    }

    override fun valueWillChange(oldValue: T, newValue: T) {
        val newTransformedValue = transform(newValue)
        if (equalityPolicy.isEqual(value, newTransformedValue)) { return }
        pendingValue = Optional.Some(newTransformedValue)
        listeners.notifyValueWillChange(value, newTransformedValue)
    }

    override fun valueDidChange(oldValue: T, newValue: T) = pendingValue.withValue {
        val oldTransformedValue = value
        value = it
        pendingValue = Optional.None
        listeners.notifyValueDidChange(oldTransformedValue, it)
    }

    override fun addListener(listener: ObservableProperty.Listener<U>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<U>): Boolean = listeners.removeListener(listener)
}

