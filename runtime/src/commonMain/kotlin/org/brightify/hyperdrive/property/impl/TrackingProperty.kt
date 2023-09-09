package org.brightify.hyperdrive.property.impl

import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.property.MutableObservableProperty
import org.brightify.hyperdrive.property.ObservableProperty

internal class TrackingProperty<T, P: ObservableProperty<T>, U>(
    private val trackedProperty: P,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<U>,
    private val read: P.(T) -> U,
    private val write: P.(U) -> Unit,
): MutableObservableProperty<U>, ObservableProperty.Listener<T> {

    private var ignoreBoundPropertyEvents = false

    private var valueStorage = trackedProperty.read(trackedProperty.value)
    override var value: U
        get() = valueStorage
        set(newValue) {
            if (equalityPolicy.isEqual(valueStorage, newValue)) { return }
            listeners.runNotifyingListeners(newValue) {
                valueStorage = it
                ignoringBoundPropertyChanges {
                    trackedProperty.write(it)
                }
            }
        }

    init {
        trackedProperty.addListener(this)
    }

    private val listeners = ValueChangeListenerHandler(this)

    private fun ignoringBoundPropertyChanges(block: () -> Unit) {
        try {
            ignoreBoundPropertyEvents = true
            block()
        } finally {
            ignoreBoundPropertyEvents = false
        }
    }

    override fun addListener(listener: ObservableProperty.Listener<U>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<U>): Unit = listeners.removeListener(listener)

    override fun valueDidChange(oldValue: T, newValue: T) {
        if (ignoreBoundPropertyEvents) { return }
        val readNewValue = trackedProperty.read(newValue)
        if (equalityPolicy.isEqual(value, readNewValue)) { return }
        listeners.runNotifyingListeners(readNewValue) {
            valueStorage = it
        }
    }
}
