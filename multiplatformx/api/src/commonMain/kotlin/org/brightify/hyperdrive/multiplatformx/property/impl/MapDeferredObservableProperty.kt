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
): DeferredObservableProperty<U>, DeferredObservableProperty.ValueChangeListener<T> {
    private val listeners = DeferredObservablePropertyListeners(this)

    override var latestValue: Optional<U> = mapped.latestValue.map(transform)

    private var pendingValue: Optional<U> = Optional.None
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

    override fun valueWillChange(oldValue: Optional<T>, newValue: T) {
        val currentValue = latestValue
        val newTransformedValue = transform(newValue)
        if (currentValue is Optional.Some && equalityPolicy.isEqual(currentValue.value, newTransformedValue)) { return }
        pendingValue = Optional.Some(newTransformedValue)
        listeners.notifyValueWillChange(currentValue, newTransformedValue)
    }

    override fun valueDidChange(oldValue: Optional<T>, newValue: T) = pendingValue.withValue {
        val oldTransformedValue = latestValue
        latestValue = Optional.Some(it)
        pendingValue = Optional.None
        valueFlow.tryEmit(it)
        listeners.notifyValueDidChange(oldTransformedValue, it)
    }

    override fun addListener(listener: DeferredObservableProperty.ValueChangeListener<U>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: DeferredObservableProperty.ValueChangeListener<U>): Boolean = listeners.removeListener(listener)
}

