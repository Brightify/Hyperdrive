package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.DeferredObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ValueChangeListener
import org.brightify.hyperdrive.multiplatformx.util.WeakListenerHandler
import org.brightify.hyperdrive.utils.Optional

internal class ValueChangeListenerHandler<OLD, NEW>(owner: Any, val getCurrentValue: () -> OLD) {
    private val listeners = WeakListenerHandler<ValueChangeListener<OLD, NEW>>(owner)

    fun <U> runNotifyingListeners(newValue: NEW, block: (NEW) -> U): U {
        val oldValue = getCurrentValue()
        notifyValueWillChange(oldValue, newValue)
        val result = block(newValue)
        notifyValueDidChange(oldValue, newValue)
        return result
    }

    fun notifyValueWillChange(oldValue: OLD, newValue: NEW) = listeners.notifyListeners {
        valueWillChange(oldValue, newValue)
    }

    fun notifyValueDidChange(oldValue: OLD, newValue: NEW) = listeners.notifyListeners {
        valueDidChange(oldValue, newValue)
    }

    fun addListener(listener: ValueChangeListener<OLD, NEW>): CancellationToken = listeners.addListener(listener)

    fun removeListener(listener: ValueChangeListener<OLD, NEW>) = listeners.removeListener(listener)

    companion object {
        operator fun <T> invoke(property: ObservableProperty<T>) = ValueChangeListenerHandler<T, T>(property) {
            property.value
        }

        operator fun <T> invoke(property: DeferredObservableProperty<T>) = ValueChangeListenerHandler<Optional<T>, T>(property) {
            property.latestValue
        }
    }
}
