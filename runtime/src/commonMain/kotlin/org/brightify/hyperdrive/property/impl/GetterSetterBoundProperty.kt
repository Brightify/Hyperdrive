package org.brightify.hyperdrive.property.impl

import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.property.MutableObservableProperty
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.property.defaultEqualityPolicy

internal class GetterSetterBoundProperty<T>(
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    private val getter: () -> T,
    private val setter: (T) -> Unit,
): MutableObservableProperty<T> {
    override var value: T = getter()
        set(newValue) {
            if (equalityPolicy.isEqual(value, newValue)) { return }
            listeners.runNotifyingListeners(newValue) {
                field = value
                setter(value)
            }
        }

    private val listeners = ValueChangeListenerHandler(this)

    override fun addListener(listener: ObservableProperty.Listener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<T>): Unit = listeners.removeListener(listener)
}
