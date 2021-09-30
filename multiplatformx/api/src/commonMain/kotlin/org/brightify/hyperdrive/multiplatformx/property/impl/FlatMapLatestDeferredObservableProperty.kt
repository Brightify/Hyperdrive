package org.brightify.hyperdrive.multiplatformx.property.impl

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.DeferredObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.utils.Optional
import org.brightify.hyperdrive.utils.flatMap
import org.brightify.hyperdrive.utils.map
import org.brightify.hyperdrive.utils.mapToKotlin
import org.brightify.hyperdrive.utils.someOrDefault
import org.brightify.hyperdrive.utils.toKotlin
import org.brightify.hyperdrive.utils.toOptional

internal class FlatMapLatestDeferredObservableProperty<T, U>(
    private val switchMapped: DeferredObservableProperty<T>,
    private val transform: (T) -> DeferredObservableProperty<U>,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<DeferredObservableProperty<U>>,
): DeferredObservableProperty<U>, DeferredObservableProperty.Listener<T> {
    private var activeBacking: DeferredObservableProperty<U>? = switchMapped.latestValue.mapToKotlin(transform)
    private var pendingActiveBacking: DeferredObservableProperty<U>? = null

    override var latestValue: Optional<U> = activeBacking.toOptional().flatMap { it.latestValue }
        private set

    private val valueFlow = MutableSharedFlow<U>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val listeners = ValueChangeListenerHandler(this)
    private val passthroughListener = PassthroughValueChangeListener()
    @Suppress("JoinDeclarationAndAssignment")
    private val switchMappingSubscriptionCancellation: CancellationToken
    private var activeBackingSubscriptionCancellation: CancellationToken?

    override suspend fun await(): U {
        return latestValue.someOrDefault {
            nextValue()
        }
    }

    override suspend fun nextValue(): U {
        return valueFlow.first()
    }

    init {
        switchMappingSubscriptionCancellation = switchMapped.addListener(this)
        activeBackingSubscriptionCancellation = activeBacking?.addListener(passthroughListener)
    }

    override fun valueWillChange(oldValue: Optional<T>, newValue: T) {
        val currentActiveBacking = activeBacking
        val newActiveBacking = transform(newValue)
        if (currentActiveBacking != null && equalityPolicy.isEqual(currentActiveBacking, newActiveBacking)) { return }

        // Only remove the listener if we intend to replace the active backing.
        activeBackingSubscriptionCancellation?.cancel()
        pendingActiveBacking = newActiveBacking

        val newActiveBackingLatestValue = newActiveBacking.latestValue
        if (newActiveBackingLatestValue is Optional.Some) {
            listeners.notifyValueWillChange(latestValue, newActiveBackingLatestValue.value)
        }
    }

    override fun valueDidChange(oldValue: Optional<T>, newValue: T) {
        val oldActiveBacking = activeBacking
        val newActiveBacking = pendingActiveBacking ?: return
        activeBacking = newActiveBacking
        pendingActiveBacking = null

        val newActiveBackingLatestValue = newActiveBacking.latestValue
        if (newActiveBackingLatestValue is Optional.Some) {
            latestValue = newActiveBackingLatestValue
            valueFlow.tryEmit(newActiveBackingLatestValue.value)
            listeners.notifyValueDidChange(oldActiveBacking?.latestValue ?: Optional.None, newActiveBackingLatestValue.value)
        }

        activeBackingSubscriptionCancellation = newActiveBacking.addListener(passthroughListener)
    }

    override fun addListener(listener: DeferredObservableProperty.Listener<U>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: DeferredObservableProperty.Listener<U>): Boolean = listeners.removeListener(listener)

    inner class PassthroughValueChangeListener: DeferredObservableProperty.Listener<U> {
        override fun valueWillChange(oldValue: Optional<U>, newValue: U) {
            if (oldValue is Optional.None) {
                listeners.notifyValueWillChange(latestValue, newValue)
            } else {
                listeners.notifyValueWillChange(oldValue, newValue)
            }
        }

        override fun valueDidChange(oldValue: Optional<U>, newValue: U) {
            val oldLatestValue = latestValue
            latestValue = Optional.Some(newValue)
            valueFlow.tryEmit(newValue)
            listeners.notifyValueDidChange(oldLatestValue, newValue)
        }
    }
}

