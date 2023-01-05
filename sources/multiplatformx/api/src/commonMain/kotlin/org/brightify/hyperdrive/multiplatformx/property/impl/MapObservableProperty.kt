package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

internal class MapObservableProperty<T, U>(
    private val mapped: ObservableProperty<T>,
    private val transform: (T) -> U,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<U>,
): ObservableProperty<U>, ObservableProperty.Listener<T> {
    override var value: U = transform(mapped.value)
        private set

    private val listeners = ValueChangeListenerHandler(this)

    init {
        mapped.addListener(this)
    }

    override fun valueDidChange(oldValue: T, newValue: T) {
        val newTransformedValue = transform(newValue)
        if (equalityPolicy.isEqual(value, newTransformedValue)) { return }
        listeners.runNotifyingListeners(newTransformedValue) {
            value = it
        }
    }

    override fun addListener(listener: ObservableProperty.Listener<U>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<U>) = listeners.removeListener(listener)
}

