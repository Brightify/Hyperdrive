package org.brightify.hyperdrive.property.impl

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.concat
import org.brightify.hyperdrive.property.DeferredObservableProperty
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.utils.Optional
import org.brightify.hyperdrive.utils.someOrDefault

internal class MergeObservableProperty<T>(
    private val sources: List<DeferredObservableProperty<T>>,
): DeferredObservableProperty<T>, DeferredObservableProperty.Listener<T> {

    private val listeners = ValueChangeListenerHandler(this)
    private val coroutineBridge = MutableSharedFlow<T>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        sources.forEach { property -> property.addListener(this) }
    }

    override fun valueDidChange(oldValue: Optional<T>, newValue: T) {
        listeners.runNotifyingListeners(newValue) {
            latestValue = Optional.Some(it)
            coroutineBridge.tryEmit(it)
        }
    }

    override fun addListener(listener: DeferredObservableProperty.Listener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: DeferredObservableProperty.Listener<T>) = listeners.removeListener(listener)

    override var latestValue: Optional<T> = Optional.None
        private set

    override suspend fun await(): T = coroutineScope {
        latestValue.someOrDefault {
            nextValue()
        }
    }

    override suspend fun nextValue(): T = coroutineScope {
        coroutineBridge.first()
    }
}
