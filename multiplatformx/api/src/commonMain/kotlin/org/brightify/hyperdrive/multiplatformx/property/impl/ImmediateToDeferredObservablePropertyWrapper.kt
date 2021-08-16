package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.DeferredObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.nextValue

internal class ImmediateToDeferredObservablePropertyWrapper<T>(
    private val wrapped: ObservableProperty<T>,
): DeferredObservableProperty<T>, ObservableProperty.ValueChangeListener<T> {
    private val listeners = DeferredObservablePropertyListeners(this)

    override fun valueWillChange(oldValue: T, newValue: T) {
        listeners.notifyValueWillChange(oldValue, newValue)
    }

    override fun valueDidChange(oldValue: T, newValue: T) {
        listeners.notifyValueDidChange(oldValue, newValue)
    }

    override fun addListener(listener: DeferredObservableProperty.ValueChangeListener<T>): CancellationToken =
        listeners.addListener(listener)

    override fun removeListener(listener: DeferredObservableProperty.ValueChangeListener<T>): Boolean =
        listeners.removeListener(listener)

    override val latestValue: T?
        get() = wrapped.value

    override suspend fun await(): T = wrapped.value

    override suspend fun nextValue(): T = wrapped.nextValue()
}