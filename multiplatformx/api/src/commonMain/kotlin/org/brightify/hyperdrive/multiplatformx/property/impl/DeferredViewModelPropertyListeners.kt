package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.DeferredViewModelProperty

internal class DeferredViewModelPropertyListeners<T>(private val backing: DeferredViewModelProperty<T>) {

    private val listeners = mutableSetOf<DeferredViewModelProperty.ValueChangeListener<T>>()

    fun <U> runNotifyingListeners(newValue: T, block: (T) -> U): U {
        val oldValue = backing.latestValue
        notifyValueWillChange(newValue)
        val result = block(newValue)
        notifyValueDidChange(oldValue)
        return result
    }

    fun notifyValueWillChange(newValue: T) {
        listeners.forEach { it.valueWillChange(newValue) }
    }

    fun notifyValueDidChange(oldValue: T?) {
        listeners.forEach { it.valueDidChange(oldValue) }
    }

    fun addListener(listener: DeferredViewModelProperty.ValueChangeListener<T>): CancellationToken {
        listeners.add(listener)
        return CancellationToken {
            removeListener(listener)
        }
    }

    fun removeListener(listener: DeferredViewModelProperty.ValueChangeListener<T>): Boolean {
        return listeners.remove(listener)
    }
}