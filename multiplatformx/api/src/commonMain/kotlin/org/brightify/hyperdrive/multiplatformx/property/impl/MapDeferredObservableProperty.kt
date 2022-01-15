package org.brightify.hyperdrive.multiplatformx.property.impl

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.DeferredObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.utils.Optional
import org.brightify.hyperdrive.utils.map
import org.brightify.hyperdrive.utils.someOrDefault

internal class MapDeferredObservableProperty<T, U>(
    private val mapped: DeferredObservableProperty<T>,
    private val transform: (T) -> U,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<U>,
): DeferredObservableProperty<U>, DeferredObservableProperty.Listener<T> {
    private val listeners = ValueChangeListenerHandler(this)

    override var latestValue: Optional<U> = mapped.latestValue.map(transform)

    private val valueFlow = MutableSharedFlow<U>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        mapped.addListener(this)
    }

    override suspend fun await(): U {
        return latestValue.someOrDefault {
            nextValue()
        }
    }

    override suspend fun nextValue(): U {
        return valueFlow.first()
    }

    override fun valueDidChange(oldValue: Optional<T>, newValue: T) {
        val currentValue = latestValue
        val newTransformedValue = transform(newValue)
        if (currentValue is Optional.Some && equalityPolicy.isEqual(currentValue.value, newTransformedValue)) { return }
        listeners.runNotifyingListeners(newTransformedValue) {
            latestValue = Optional.Some(it)
            valueFlow.tryEmit(it)
        }
    }

    override fun addListener(listener: DeferredObservableProperty.Listener<U>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: DeferredObservableProperty.Listener<U>): Boolean = listeners.removeListener(listener)
}

