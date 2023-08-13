package org.brightify.hyperdrive.property.impl

import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.concat
import org.brightify.hyperdrive.property.ObservableProperty

internal class CombineLatestObservableProperty<T>(
    private val sources: List<ObservableProperty<T>>,
): ObservableProperty<List<T>> {
    override var value: List<T> = sources.map { it.value }
        private set

    private val listeners = ValueChangeListenerHandler(this)
    // Required to keep the IndexListener instances retained.
    private val listenerRegistration = sources.mapIndexed { index, property -> property.addListener(IndexListener(index)) }.concat()

    override fun addListener(listener: ObservableProperty.Listener<List<T>>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<List<T>>) = listeners.removeListener(listener)

    private inner class IndexListener(private val index: Int): ObservableProperty.Listener<T> {
        override fun valueDidChange(oldValue: T, newValue: T) {
            val oldList = value
            val newList = oldList.toMutableList()
            newList[index] = newValue
            listeners.runNotifyingListeners(newList) {
                value = newList
            }
        }
    }
}
