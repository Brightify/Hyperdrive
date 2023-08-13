package org.brightify.hyperdrive.property.impl

import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.property.MutableObservableProperty
import org.brightify.hyperdrive.property.ObservableProperty

internal class ValueObservableProperty<T>(
    initialValue: T,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T>,
): MutableObservableProperty<T> {
    override var value: T = initialValue
        set(newValue) {
            if (equalityPolicy.isEqual(field, newValue)) { return }
            listeners.runNotifyingListeners(newValue) {
                field = it
            }
        }

    private val listeners = ValueChangeListenerHandler(this)

    override fun addListener(listener: ObservableProperty.Listener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<T>) = listeners.removeListener(listener)
}
