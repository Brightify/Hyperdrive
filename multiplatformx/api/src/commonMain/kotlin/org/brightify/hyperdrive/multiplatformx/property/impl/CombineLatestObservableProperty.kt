package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.concat
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

internal class CombineLatestObservableProperty<T>(
    sources: List<ObservableProperty<T>>,
): ObservableProperty<List<T>> {
    override var value: List<T> = sources.map { it.value }
        private set

    private val listeners = ValueChangeListenerHandler(this)

    init {
        sources.mapIndexed { index, property -> property.addListener(IndexListener(index)) }.concat()
    }

    override fun addListener(listener: ObservableProperty.Listener<List<T>>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<List<T>>): Boolean = listeners.removeListener(listener)

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