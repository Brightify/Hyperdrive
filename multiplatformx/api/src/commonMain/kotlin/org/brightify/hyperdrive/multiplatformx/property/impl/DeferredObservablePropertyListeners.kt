package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.DeferredObservableProperty

internal class DeferredObservablePropertyListeners<T>(private val backing: DeferredObservableProperty<T>) {

    private val listeners = mutableSetOf<DeferredObservableProperty.ValueChangeListener<T>>()

    fun <U> runNotifyingListeners(newValue: T, block: (T) -> U): U {
        val oldValue = backing.latestValue
        notifyValueWillChange(oldValue, newValue)
        val result = block(newValue)
        notifyValueDidChange(oldValue, newValue)
        return result
    }

    fun notifyValueWillChange(oldValue: T?, newValue: T) {
        listeners.forEach { it.valueWillChange(oldValue, newValue) }
    }

    fun notifyValueDidChange(oldValue: T?, newValue: T) {
        listeners.forEach { it.valueDidChange(oldValue, newValue) }
    }

    fun addListener(listener: DeferredObservableProperty.ValueChangeListener<T>): CancellationToken {
        listeners.add(listener)
        return CancellationToken {
            removeListener(listener)
        }
    }

    fun removeListener(listener: DeferredObservableProperty.ValueChangeListener<T>): Boolean {
        return listeners.remove(listener)
    }
}