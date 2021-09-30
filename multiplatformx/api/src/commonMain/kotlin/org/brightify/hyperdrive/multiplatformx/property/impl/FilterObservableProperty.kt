package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.utils.Optional

internal class FilterObservableProperty<T>(
    private val filtered: ObservableProperty<T>,
    initialValue: T,
    private val predicate: (T) -> Boolean,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T>,
): ObservableProperty<T>, ObservableProperty.Listener<T> {

    override var value: T = initialValue
        private set
    private var pendingValue: Optional<T> = Optional.None

    private val listeners = ValueChangeListenerHandler(this)

    init {
        filtered.addListener(this)
    }

    override fun valueWillChange(oldValue: T, newValue: T) {
        if (!predicate(newValue)) { return }

        val oldValue = value
        val shouldSave = oldValue == null || equalityPolicy.isEqual(oldValue, newValue)
        if (shouldSave) {
            pendingValue = Optional.Some(newValue)
            listeners.notifyValueWillChange(oldValue, newValue)
        }
    }

    override fun valueDidChange(oldValue: T, newValue: T) = pendingValue.withValue {
        val oldFilteredValue = value
        value = it
        pendingValue = Optional.None
        listeners.notifyValueDidChange(oldFilteredValue, it)
    }

    override fun addListener(listener: ObservableProperty.Listener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<T>): Boolean = listeners.removeListener(listener)
}