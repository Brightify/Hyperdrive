package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.MutableObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

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

    private val listeners = ObservablePropertyListeners(this)

    override fun addListener(listener: ObservableProperty.ValueChangeListener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.ValueChangeListener<T>): Boolean = listeners.removeListener(listener)
}

