package org.brightify.hyperdrive.property.impl

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.Lifecycle
import org.brightify.hyperdrive.property.DeferredObservableProperty
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.util.AsyncQueue
import org.brightify.hyperdrive.utils.Optional
import org.brightify.hyperdrive.utils.filterSome
import org.brightify.hyperdrive.utils.someOrDefault

internal class AsyncMapDeferredObservableProperty<T, U>(
    private val source: ObservableProperty<T>,
    private val asyncMap: suspend (T) -> U,
    private val lifecycle: Lifecycle,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<U>,
    private val overflowPolicy: AsyncQueue.OverflowPolicy,
): DeferredObservableProperty<U>, ObservableProperty.Listener<T> {
    override val latestValue: Optional<U>
        get() = storage.value

    private val listeners = ValueChangeListenerHandler(this)
    // FIXME: This won't support identityEqualityPolicy/neverEqualityPolicy!
    private val storage = MutableStateFlow<Optional<U>>(Optional.None)

    private val queue = AsyncQueue<T>(overflowPolicy, lifecycle) {
        val newValue = asyncMap(it)
        val oldMappedValue = storage.value
        val shouldSave = oldMappedValue !is Optional.Some || equalityPolicy.isEqual(oldMappedValue.value, newValue)
        if (shouldSave) {
            listeners.runNotifyingListeners(newValue) {
                storage.value = Optional.Some(it)
            }
        }
    }

    init {
        source.addListener(this)
    }

    override fun valueDidChange(oldValue: T, newValue: T) {
        queue.push(newValue)
    }

    override suspend fun await(): U = coroutineScope {
        storage.value.someOrDefault { storage.filterSome().first() }
    }

    override suspend fun nextValue(): U = coroutineScope {
        storage.drop(1).filterSome().first()
    }

    override fun addListener(listener: DeferredObservableProperty.Listener<U>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: DeferredObservableProperty.Listener<U>) = listeners.removeListener(listener)
}
