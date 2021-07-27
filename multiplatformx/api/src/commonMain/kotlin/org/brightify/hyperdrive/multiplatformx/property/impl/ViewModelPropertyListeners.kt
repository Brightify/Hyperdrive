package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty

internal class ViewModelPropertyListeners<T>(private val backing: ViewModelProperty<T>) {
    private val listeners = mutableSetOf<ViewModelProperty.ValueChangeListener<T>>()

    fun <U> runNotifyingListeners(newValue: T, block: (T) -> U): U {
        val oldValue = backing.value
        notifyValueWillChange(newValue)
        val result = block(newValue)
        notifyValueDidChange(oldValue)
        return result
    }

    fun notifyValueWillChange(newValue: T) {
        listeners.forEach { it.valueWillChange(newValue) }
    }

    fun notifyValueDidChange(oldValue: T) {
        listeners.forEach { it.valueDidChange(oldValue) }
    }

    fun addListener(listener: ViewModelProperty.ValueChangeListener<T>): CancellationToken {
        listeners.add(listener)
        return CancellationToken {
            removeListener(listener)
        }
    }

    fun removeListener(listener: ViewModelProperty.ValueChangeListener<T>): Boolean {
        return listeners.remove(listener)
    }
}

