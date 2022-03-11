package org.brightify.hyperdrive.multiplatformx.property.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.DeferredObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.utils.Optional
import org.brightify.hyperdrive.utils.filterSome
import org.brightify.hyperdrive.utils.someOrDefault

internal class DeferredFilterObservableProperty<T>(
    private val filtered: ObservableProperty<T>,
    private val predicate: (T) -> Boolean,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T>,
): DeferredObservableProperty<T>, ObservableProperty.Listener<T> {
    override val latestValue: Optional<T>
        get() = storage.value

    private val listeners = ValueChangeListenerHandler(this)
    // FIXME: This won't support identityEqualityPolicy/neverEqualityPolicy!
    private val storage = MutableStateFlow(filtered.value.let {
        if (predicate(it)) {
            Optional.Some(it)
        } else {
            Optional.None
        }
    })

    init {
        filtered.addListener(this)
    }

    override suspend fun await(): T {
        return storage.value.someOrDefault {
            storage.filterSome().first()
        }
    }

    override suspend fun nextValue(): T {
        return storage.drop(1).filterSome().first()
    }

    override fun valueDidChange(oldValue: T, newValue: T) {
        if (!predicate(newValue)) { return }

        val oldFilteredValue = storage.value
        val shouldSave = oldFilteredValue !is Optional.Some || equalityPolicy.isEqual(oldFilteredValue.value, newValue)
        if (shouldSave) {
            listeners.runNotifyingListeners(newValue) {
                storage.value = Optional.Some(it)
            }
        }
    }

    override fun addListener(listener: DeferredObservableProperty.Listener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: DeferredObservableProperty.Listener<T>) = listeners.removeListener(listener)
}

