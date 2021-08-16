package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.DeferredObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

internal class DeferredToImmediateObservablePropertyWrapper<T>(
    private val initialValue: T,
    private val wrapped: DeferredObservableProperty<T>,
): ObservableProperty<T>, DeferredObservableProperty.ValueChangeListener<T> {
    private val listeners = ObservablePropertyListeners(this)

    override var value: T = initialValue
        private set

    private var pendingValue: T? = null

    init {
        wrapped.addListener(this)
    }

    override fun valueWillChange(oldValue: T?, newValue: T) {
        pendingValue = newValue
        listeners.notifyValueWillChange(value, newValue)
    }

    override fun valueDidChange(oldValue: T?, newValue: T) {
        value = pendingValue ?: return
        listeners.notifyValueDidChange(oldValue ?: initialValue, value)
    }

    override fun addListener(listener: ObservableProperty.ValueChangeListener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.ValueChangeListener<T>): Boolean = listeners.removeListener(listener)
}
