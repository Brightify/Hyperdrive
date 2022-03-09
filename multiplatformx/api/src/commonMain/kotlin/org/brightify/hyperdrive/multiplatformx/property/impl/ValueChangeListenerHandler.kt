package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.impl.BaseCancellationToken
import org.brightify.hyperdrive.multiplatformx.property.DeferredObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ValueChangeListener
import org.brightify.hyperdrive.utils.Optional
import org.brightify.hyperdrive.utils.WeakReference

internal class ValueChangeListenerHandler<OLD, NEW>(private val getCurrentValue: () -> OLD) {
    private val listeners = mutableSetOf<ValueChangeListener<OLD, NEW>>()
    private var isNotifyingListeners = false
    private val pendingListenerModifications = mutableListOf<ListenerModification<OLD, NEW>>()

    fun <U> runNotifyingListeners(newValue: NEW, block: (NEW) -> U): U {
        val oldValue = getCurrentValue()
        notifyValueWillChange(oldValue, newValue)
        val result = block(newValue)
        notifyValueDidChange(oldValue, newValue)
        return result
    }

    fun notifyValueWillChange(oldValue: OLD, newValue: NEW) = trackNotifyingListeners {
        listeners.forEach { it.valueWillChange(oldValue, newValue) }
    }

    fun notifyValueDidChange(oldValue: OLD, newValue: NEW) = trackNotifyingListeners {
        listeners.forEach { it.valueDidChange(oldValue, newValue) }
    }

    fun addListener(listener: ValueChangeListener<OLD, NEW>): CancellationToken {
        if (isNotifyingListeners) {
            pendingListenerModifications.add(ListenerModification.Add(listener))
        } else {
            listeners.add(listener)
        }
        return CancellationToken {
            removeListener(listener)
        }
    }

    fun removeListener(listener: ValueChangeListener<OLD, NEW>): Boolean {
        return if (isNotifyingListeners) {
            pendingListenerModifications.removeAll { it is ListenerModification.Add && it.listener == listener } ||
                pendingListenerModifications.any { it is ListenerModification.Remove && it.listener == listener } ||
                pendingListenerModifications.add(ListenerModification.Remove(listener))
        } else {
            listeners.remove(listener)
        }
    }

    private inline fun trackNotifyingListeners(block: () -> Unit) {
        check(!isNotifyingListeners) { "Reentrancy! Value was changed while notifying listeners!" }
        try {
            isNotifyingListeners = true
            block()
        } finally {
            isNotifyingListeners = false
            if (pendingListenerModifications.isNotEmpty()) {
                pendingListenerModifications.forEach { modification ->
                    when (modification) {
                        is ListenerModification.Add -> listeners.add(modification.listener)
                        is ListenerModification.Remove -> listeners.remove(modification.listener)
                    }
                }
                pendingListenerModifications.clear()
            }
        }
    }

    companion object {
        operator fun <T> invoke(property: ObservableProperty<T>) = ValueChangeListenerHandler<T, T> {
            property.value
        }

        operator fun <T> invoke(property: DeferredObservableProperty<T>) = ValueChangeListenerHandler<Optional<T>, T> {
            property.latestValue
        }
    }

    private sealed interface ListenerModification<OLD, NEW> {
        class Add<OLD, NEW>(val listener: ValueChangeListener<OLD, NEW>): ListenerModification<OLD, NEW>
        class Remove<OLD, NEW>(val listener: ValueChangeListener<OLD, NEW>): ListenerModification<OLD, NEW>
    }
}
