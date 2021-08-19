package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.impl.BaseCancellationToken
import org.brightify.hyperdrive.multiplatformx.property.DeferredObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.utils.WeakReference

internal class DeferredObservablePropertyListeners<T>(private val backing: DeferredObservableProperty<T>) {
    private val listeners = mutableSetOf<WeakReference<DeferredObservableProperty.ValueChangeListener<T>>>()

    fun <U> runNotifyingListeners(newValue: T, block: (T) -> U): U {
        val oldValue = backing.latestValue
        notifyValueWillChange(oldValue, newValue)
        val result = block(newValue)
        notifyValueDidChange(oldValue, newValue)
        return result
    }

    fun notifyValueWillChange(oldValue: T?, newValue: T) {
        listeners.forEach {
            it.get()?.valueWillChange(oldValue, newValue)
        }
    }

    fun notifyValueDidChange(oldValue: T?, newValue: T) {
        listeners.forEach {
            it.get()?.valueDidChange(oldValue, newValue)
        }
    }

    fun addListener(listener: DeferredObservableProperty.ValueChangeListener<T>): CancellationToken {
        val reference = WeakReference(listener)
        listeners.add(reference)
        return CancellationToken {
            removeListener(listener)
        }
    }

    fun removeListener(listener: DeferredObservableProperty.ValueChangeListener<T>): Boolean {
        return listeners.removeAll { reference ->
            reference.get()?.let { it == listener } ?: true
        }
    }
}
