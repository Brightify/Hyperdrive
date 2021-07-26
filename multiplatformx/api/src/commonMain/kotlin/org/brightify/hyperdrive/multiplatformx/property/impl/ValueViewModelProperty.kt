package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.MutableViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty

internal class ValueViewModelProperty<T>(
    initialValue: T,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<T>,
): MutableViewModelProperty<T> {
    override var value: T = initialValue
        set(newValue) {
            if (equalityPolicy.isEqual(field, newValue)) { return }
            listeners.runNotifyingListeners(newValue) {
                field = it
            }
        }

    private val listeners = ViewModelPropertyListeners(this)

    override fun addListener(listener: ViewModelProperty.ValueChangeListener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ViewModelProperty.ValueChangeListener<T>): Boolean = listeners.removeListener(listener)
}