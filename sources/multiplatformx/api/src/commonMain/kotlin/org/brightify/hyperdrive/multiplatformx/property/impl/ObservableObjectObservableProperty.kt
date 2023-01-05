package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.ObservableObject
import org.brightify.hyperdrive.multiplatformx.property.MutableObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

internal class ObservableObjectObservableProperty<T: ObservableObject>(
    initialValue: T,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T>,
): MutableObservableProperty<T>, ObservableObject.ChangeTracking.Listener {
    override var value: T = initialValue
        set(newValue) {
            if (equalityPolicy.isEqual(field, newValue)) { return }
            field.changeTracking.removeListener(this)
            listeners.runNotifyingListeners(newValue) {
                field = it
            }
            field.changeTracking.addListener(this)
        }

    init {
        initialValue.changeTracking.addListener(this)
    }

    override fun onObjectWillChange() {
        listeners.notifyValueWillChange(value, value)
    }

    override fun onObjectDidChange() {
        listeners.notifyValueDidChange(value, value)
    }

    private val listeners = ValueChangeListenerHandler(this)

    override fun addListener(listener: ObservableProperty.Listener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<T>) = listeners.removeListener(listener)
}