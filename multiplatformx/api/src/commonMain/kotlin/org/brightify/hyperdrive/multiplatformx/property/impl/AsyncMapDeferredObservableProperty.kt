package org.brightify.hyperdrive.multiplatformx.property.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.Lifecycle
import org.brightify.hyperdrive.multiplatformx.property.DeferredObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.multiplatformx.util.AsyncQueue

internal class AsyncMapDeferredObservableProperty<T, U>(
    private val source: ObservableProperty<T>,
    private val asyncMap: suspend (T) -> U,
    private val lifecycle: Lifecycle,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<U>,
    private val overflowPolicy: AsyncQueue.OverflowPolicy,
): DeferredObservableProperty<U>, ObservableProperty.ValueChangeListener<T> {
    override val latestValue: U?
        get() = storage.value

    private val listeners = DeferredObservablePropertyListeners(this)
    private val storage = MutableStateFlow<U?>(null)

    private val queue = AsyncQueue<T>(overflowPolicy, lifecycle) {
        val newValue = asyncMap(it)
        val oldMappedValue = storage.value
        val shouldSave = oldMappedValue == null || equalityPolicy.isEqual(oldMappedValue, newValue)
        if (shouldSave) {
            listeners.runNotifyingListeners(newValue) {
                storage.value = it
            }
        }
    }

    init {
        source.addListener(this)
    }

    override fun valueDidChange(oldValue: T, newValue: T) {
        queue.push(newValue)
    }

    override suspend fun await(): U {
        return storage.value ?: storage.mapNotNull { it }.first()
    }

    override suspend fun nextValue(): U {
        return storage.drop(1).mapNotNull { it }.first()
    }

    override fun addListener(listener: DeferredObservableProperty.ValueChangeListener<U>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: DeferredObservableProperty.ValueChangeListener<U>): Boolean = listeners.removeListener(listener)
}