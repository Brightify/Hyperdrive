package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.concat
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

internal class FlatMapLatestObservableProperty<T, U>(
    private val switchMapped: ObservableProperty<T>,
    private val transform: (T) -> ObservableProperty<U>,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<ObservableProperty<U>>,
): ObservableProperty<U>, ObservableProperty.ValueChangeListener<T> {
    private var activeBacking: ObservableProperty<U> = transform(switchMapped.value)
    private var pendingActiveBacking: ObservableProperty<U>? = null

    override val value: U
        get() = activeBacking.value

    private val listeners = ObservablePropertyListeners(this)
    private val passthroughListener = PassthroughValueChangeListener()
    @Suppress("JoinDeclarationAndAssignment")
    private val switchMappingSubscriptionCancellation: CancellationToken
    private var activeBackingSubscriptionCancellation: CancellationToken

    init {
        switchMappingSubscriptionCancellation = switchMapped.addListener(this)
        activeBackingSubscriptionCancellation = activeBacking.addListener(passthroughListener)
    }

    override fun valueWillChange(oldValue: T, newValue: T) {
        val newActiveBacking = transform(newValue)
        if (equalityPolicy.isEqual(activeBacking, newActiveBacking)) { return }
        pendingActiveBacking = newActiveBacking
        listeners.notifyValueWillChange(newActiveBacking.value, activeBacking.value)
    }

    override fun valueDidChange(oldValue: T, newValue: T) {
        val oldActiveBacking = activeBacking
        activeBacking = pendingActiveBacking ?: return
        pendingActiveBacking = null

        // Only remove the listener if we replaced the active backing.
        activeBackingSubscriptionCancellation.cancel()
        listeners.notifyValueDidChange(oldActiveBacking.value, activeBacking.value)
        activeBackingSubscriptionCancellation = activeBacking.addListener(passthroughListener)
    }

    override fun addListener(listener: ObservableProperty.ValueChangeListener<U>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.ValueChangeListener<U>): Boolean = listeners.removeListener(listener)

    inner class PassthroughValueChangeListener: ObservableProperty.ValueChangeListener<U> {
        override fun valueWillChange(oldValue: U, newValue: U) {
            listeners.notifyValueWillChange(newValue, newValue)
        }

        override fun valueDidChange(oldValue: U, newValue: U) {
            listeners.notifyValueDidChange(oldValue, newValue)
        }
    }
}

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