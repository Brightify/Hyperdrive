package org.brightify.hyperdrive.property.impl

import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.property.ObservableProperty

internal class ConstantObservableProperty<T>(
    override val value: T
): ObservableProperty<T> {
    private val listeners = ValueChangeListenerHandler(this)

    override fun addListener(listener: ObservableProperty.Listener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<T>) = listeners.removeListener(listener)
}
