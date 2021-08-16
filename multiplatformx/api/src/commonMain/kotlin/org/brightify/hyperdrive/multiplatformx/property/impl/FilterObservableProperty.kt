package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

internal class FilterObservableProperty<T>(
    private val filtered: ObservableProperty<T>,
    initialValue: T,
    private val predicate: (T) -> Boolean,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T>,
): ObservableProperty<T>, ObservableProperty.ValueChangeListener<T> {

    override var value: T = initialValue
        private set
    private var pendingValue: T? = null

    private val listeners = ObservablePropertyListeners(this)

    init {
        filtered.addListener(this)
    }

    override fun valueWillChange(oldValue: T, newValue: T) {
        if (!predicate(newValue)) { return }

        val oldValue = value
        val shouldSave = oldValue == null || equalityPolicy.isEqual(oldValue, newValue)
        if (shouldSave) {
            pendingValue = newValue
            listeners.notifyValueWillChange(oldValue, newValue)
        }
    }

    override fun valueDidChange(oldValue: T, newValue: T) {
        val oldFilteredValue = value
        value = pendingValue ?: return
        pendingValue = null
        listeners.notifyValueDidChange(oldFilteredValue, value)
    }

    override fun addListener(listener: ObservableProperty.ValueChangeListener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.ValueChangeListener<T>): Boolean = listeners.removeListener(listener)
}