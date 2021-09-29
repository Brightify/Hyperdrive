package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.concat
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

internal class CombineLatestObservableProperty<T>(
    sources: List<ObservableProperty<T>>,
): ObservableProperty<List<T>> {

    private var pendingValue: MutableList<T>? = null
    override var value: List<T> = sources.map { it.value }
        private set

    private val listeners = ObservablePropertyListeners(this)

    init {
        sources.mapIndexed { index, property -> property.addListener(IndexListener(index)) }.concat()
    }

    override fun addListener(listener: ObservableProperty.ValueChangeListener<List<T>>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.ValueChangeListener<List<T>>): Boolean = listeners.removeListener(listener)

    private inner class IndexListener(private val index: Int): ObservableProperty.ValueChangeListener<T> {
        override fun valueWillChange(oldValue: T, newValue: T) {
            val pendingList = pendingValue ?: value.toMutableList().also { pendingValue = it }
            pendingList[index] = newValue
            listeners.notifyValueWillChange(value, pendingList)
        }

        override fun valueDidChange(oldValue: T, newValue: T) {
            val oldList = value
            val newList = pendingValue ?: return
            pendingValue = null
            value = newList
            listeners.notifyValueDidChange(oldList, newList)
        }
    }
}