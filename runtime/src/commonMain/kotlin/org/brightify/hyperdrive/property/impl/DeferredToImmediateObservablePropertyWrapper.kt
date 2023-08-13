package org.brightify.hyperdrive.property.impl

import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.property.DeferredObservableProperty
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.utils.Optional

internal class DeferredToImmediateObservablePropertyWrapper<T>(
    private val initialValue: T,
    private val wrapped: DeferredObservableProperty<T>,
): ObservableProperty<T>, DeferredObservableProperty.Listener<T> {
    private val listeners = ValueChangeListenerHandler(this)

    override var value: T = initialValue
        private set

    init {
        wrapped.addListener(this)
    }

    override fun valueDidChange(oldValue: Optional<T>, newValue: T) {
        listeners.runNotifyingListeners(newValue) {
            value = it
        }
    }

    override fun addListener(listener: ObservableProperty.Listener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<T>) = listeners.removeListener(listener)
}
