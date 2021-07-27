package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.DeferredViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.nextValue

internal class ImmediateToDeferredViewModelPropertyWrapper<T>(
    private val wrapped: ViewModelProperty<T>,
): DeferredViewModelProperty<T>, ViewModelProperty.ValueChangeListener<T> {
    private val listeners = DeferredViewModelPropertyListeners(this)

    override fun valueWillChange(newValue: T) {
        listeners.notifyValueWillChange(newValue)
    }

    override fun valueDidChange(oldValue: T) {
        listeners.notifyValueDidChange(oldValue)
    }

    override fun addListener(listener: DeferredViewModelProperty.ValueChangeListener<T>): CancellationToken =
        listeners.addListener(listener)

    override fun removeListener(listener: DeferredViewModelProperty.ValueChangeListener<T>): Boolean =
        listeners.removeListener(listener)

    override val latestValue: T?
        get() = wrapped.value

    override suspend fun await(): T = wrapped.value

    override suspend fun nextValue(): T = wrapped.nextValue()
}