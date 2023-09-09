package org.brightify.hyperdrive.property.impl

import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.property.MutableObservableProperty
import org.brightify.hyperdrive.property.ObservableProperty

internal class MutableObservablePropertyBoundProperty<T>(
    private val boundProperty: MutableObservableProperty<T>,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T>,
    private val localWillChange: (T) -> Unit,
    private val localDidChange: (T) -> Unit,
    private val boundWillChange: (T) -> Unit,
    private val boundDidChange: (T) -> Unit,
): MutableObservableProperty<T>, ObservableProperty.Listener<T> {

    private var ignoreBoundPropertyEvents = false

    private var valueStorage = boundProperty.value
    override var value: T
        get() = valueStorage
        set(newValue) {
            if (equalityPolicy.isEqual(valueStorage, newValue)) { return }
            localWillChange(newValue)
            listeners.runNotifyingListeners(newValue) {
                valueStorage = it
                ignoringBoundPropertyChanges {
                    boundProperty.value = it
                }
            }
            localDidChange(newValue)
        }

    init {
        boundProperty.addListener(this)
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

    override fun addListener(listener: ObservableProperty.Listener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<T>): Unit = listeners.removeListener(listener)

    override fun valueWillChange(oldValue: T, newValue: T) {
        if (ignoreBoundPropertyEvents) { return }
        boundWillChange(newValue)
    }

    override fun valueDidChange(oldValue: T, newValue: T) {
        if (ignoreBoundPropertyEvents) { return }
        if (equalityPolicy.isEqual(value, newValue)) { return }
        listeners.runNotifyingListeners(newValue) {
            valueStorage = it
        }
        boundDidChange(newValue)
    }
}
