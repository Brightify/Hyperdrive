package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

internal class FlatMapLatestObservableProperty<T, U>(
    private val switchMapped: ObservableProperty<T>,
    private val transform: (T) -> ObservableProperty<U>,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<ObservableProperty<U>>,
): ObservableProperty<U>, ObservableProperty.Listener<T> {
    private var activeBacking: ObservableProperty<U> = transform(switchMapped.value)

    override val value: U
        get() = activeBacking.value

    private val listeners = ValueChangeListenerHandler(this)
    private val passthroughListener = PassthroughValueChangeListener()
    @Suppress("JoinDeclarationAndAssignment")
    private val switchMappingSubscriptionCancellation: CancellationToken
    private var activeBackingSubscriptionCancellation: CancellationToken

    init {
        switchMappingSubscriptionCancellation = switchMapped.addListener(this)
        activeBackingSubscriptionCancellation = activeBacking.addListener(passthroughListener)
    }

    override fun valueDidChange(oldValue: T, newValue: T) {
        val newActiveBacking = transform(newValue)
        if (equalityPolicy.isEqual(activeBacking, newActiveBacking)) { return }

        // Only remove the listener if we will replace the active backing.
        activeBackingSubscriptionCancellation.cancel()

        val oldBackingValue = value
        val newBackingValue = newActiveBacking.value
        listeners.notifyValueWillChange(oldBackingValue, newBackingValue)
        activeBacking = newActiveBacking
        listeners.notifyValueDidChange(oldBackingValue, newBackingValue)

        activeBackingSubscriptionCancellation = activeBacking.addListener(passthroughListener)
    }

    override fun addListener(listener: ObservableProperty.Listener<U>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<U>) = listeners.removeListener(listener)

    inner class PassthroughValueChangeListener: ObservableProperty.Listener<U> {
        override fun valueWillChange(oldValue: U, newValue: U) {
            listeners.notifyValueWillChange(oldValue, newValue)
        }

        override fun valueDidChange(oldValue: U, newValue: U) {
            listeners.notifyValueDidChange(oldValue, newValue)
        }
    }
}

