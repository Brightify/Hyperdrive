package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty

internal class MappingViewModelProperty<T, U>(
    private val mapped: ViewModelProperty<T>,
    private val transform: (T) -> U,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<U>,
): ViewModelProperty<U>, ViewModelProperty.ValueChangeListener<T> {
    override var value: U = transform(mapped.value)
        private set
    private var pendingValue: U? = null

    private val listeners = ViewModelPropertyListeners(this)

    init {
        mapped.addListener(this)
    }

    override fun valueWillChange(newValue: T) {
        val newTransformedValue = transform(newValue)
        if (equalityPolicy.isEqual(value, newTransformedValue)) { return }
        pendingValue = newTransformedValue
        listeners.notifyValueWillChange(newTransformedValue)
    }

    override fun valueDidChange(oldValue: T) {
        val oldTransformedValue = value
        value = pendingValue ?: return
        pendingValue = null
        listeners.notifyValueDidChange(oldTransformedValue)
    }

    override fun addListener(listener: ViewModelProperty.ValueChangeListener<U>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ViewModelProperty.ValueChangeListener<U>): Boolean = listeners.removeListener(listener)
}

