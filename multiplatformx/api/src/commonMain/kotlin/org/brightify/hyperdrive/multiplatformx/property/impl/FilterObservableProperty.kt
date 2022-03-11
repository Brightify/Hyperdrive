package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

internal class FilterObservableProperty<T>(
    private val filtered: ObservableProperty<T>,
    initialValue: T,
    private val predicate: (T) -> Boolean,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T>,
): ObservableProperty<T>, ObservableProperty.Listener<T> {

    override var value: T = initialValue
        private set

    private val listeners = ValueChangeListenerHandler(this)

    init {
        filtered.addListener(this)
    }

    override fun valueDidChange(oldValue: T, newValue: T) {
        if (!predicate(newValue)) { return }

        val oldFilteredValue = value
        if (equalityPolicy.isEqual(oldFilteredValue, newValue)) { return }

        listeners.runNotifyingListeners(newValue) {
            value = it
        }
    }

    override fun addListener(listener: ObservableProperty.Listener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<T>) = listeners.removeListener(listener)
}
