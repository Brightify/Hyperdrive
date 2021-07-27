package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty

internal class FilteringViewModelProperty<T>(
    private val filtered: ViewModelProperty<T>,
    initialValue: T,
    private val predicate: (T) -> Boolean,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<T>,
): ViewModelProperty<T>, ViewModelProperty.ValueChangeListener<T> {

    override var value: T = initialValue
        private set
    private var pendingValue: T? = null

    private val listeners = ViewModelPropertyListeners(this)

    init {
        filtered.addListener(this)
    }

    override fun valueWillChange(newValue: T) {
        if (!predicate(newValue)) { return }

        val oldValue = value
        val shouldSave = oldValue == null || equalityPolicy.isEqual(oldValue, newValue)
        if (shouldSave) {
            pendingValue = newValue
            listeners.notifyValueWillChange(newValue)
        }
    }

    override fun valueDidChange(oldValue: T) {
        val oldFilteredValue = value
        value = pendingValue ?: return
        pendingValue = null
        listeners.notifyValueDidChange(oldFilteredValue)
    }

    override fun addListener(listener: ViewModelProperty.ValueChangeListener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ViewModelProperty.ValueChangeListener<T>): Boolean = listeners.removeListener(listener)
}