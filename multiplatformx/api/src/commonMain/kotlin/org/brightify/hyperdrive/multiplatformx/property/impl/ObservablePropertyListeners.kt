package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.impl.BaseCancellationToken
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.utils.WeakReference

internal class ObservablePropertyListeners<T>(private val backing: ObservableProperty<T>) {
    private val listeners = mutableSetOf<ObservableProperty.ValueChangeListener<T>>()

    fun <U> runNotifyingListeners(newValue: T, block: (T) -> U): U {
        val oldValue = backing.value
        notifyValueWillChange(oldValue, newValue)
        val result = block(newValue)
        notifyValueDidChange(oldValue, newValue)
        return result
    }

    fun notifyValueWillChange(oldValue: T, newValue: T) {
        listeners.forEach { it.valueWillChange(oldValue, newValue) }
    }

    fun notifyValueDidChange(oldValue: T, newValue: T) {
        listeners.forEach { it.valueDidChange(oldValue, newValue) }
    }

    fun addListener(listener: ObservableProperty.ValueChangeListener<T>): CancellationToken {
        listeners.add(listener)
        return CancellationToken {
            removeListener(listener)
        }
    }

    fun removeListener(listener: ObservableProperty.ValueChangeListener<T>): Boolean {
        return listeners.remove(listener)
    }
}
