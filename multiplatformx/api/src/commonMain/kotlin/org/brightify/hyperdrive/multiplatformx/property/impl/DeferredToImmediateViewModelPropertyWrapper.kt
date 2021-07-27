package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.DeferredViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty

internal class DeferredToImmediateViewModelPropertyWrapper<T>(
    private val initialValue: T,
    private val wrapped: DeferredViewModelProperty<T>,
): ViewModelProperty<T>, DeferredViewModelProperty.ValueChangeListener<T> {
    private val listeners = ViewModelPropertyListeners(this)

    override var value: T = initialValue
        private set

    private var pendingValue: T? = null

    init {
        wrapped.addListener(this)
    }

    override fun valueWillChange(newValue: T) {
        pendingValue = newValue
        listeners.notifyValueWillChange(newValue)
    }

    override fun valueDidChange(oldValue: T?) {
        value = pendingValue ?: return
        listeners.notifyValueDidChange(oldValue ?: initialValue)
    }

    override fun addListener(listener: ViewModelProperty.ValueChangeListener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ViewModelProperty.ValueChangeListener<T>): Boolean = listeners.removeListener(listener)
}
