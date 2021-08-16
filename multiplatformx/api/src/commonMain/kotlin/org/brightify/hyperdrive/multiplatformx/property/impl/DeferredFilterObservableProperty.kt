package org.brightify.hyperdrive.multiplatformx.property.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.DeferredObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

internal class DeferredFilterObservableProperty<T>(
    private val filtered: ObservableProperty<T>,
    private val predicate: (T) -> Boolean,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T>,
): DeferredObservableProperty<T>, ObservableProperty.ValueChangeListener<T> {
    override val latestValue: T?
        get() = storage.value
    private var pendingValue: T? = null

    private val listeners = DeferredObservablePropertyListeners(this)
    private val storage = MutableStateFlow<T?>(null)

    init {
        filtered.addListener(this)
    }

    override suspend fun await(): T {
        return storage.value ?: storage.mapNotNull { it }.first()
    }

    override suspend fun nextValue(): T {
        return storage.drop(1).mapNotNull { it }.first()
    }

    override fun valueWillChange(oldValue: T, newValue: T) {
        if (!predicate(newValue)) { return }

        val oldFilteredValue = storage.value
        val shouldSave = oldFilteredValue == null || equalityPolicy.isEqual(oldFilteredValue, newValue)
        if (shouldSave) {
            pendingValue = newValue
            listeners.notifyValueWillChange(oldFilteredValue, newValue)
        }
    }

    override fun valueDidChange(oldValue: T, newValue: T) {
        val oldFilteredValue = storage.value
        val newFilteredValue = pendingValue ?: return
        storage.value = newFilteredValue
        pendingValue = null
        listeners.notifyValueDidChange(oldFilteredValue, newFilteredValue)
    }

    override fun addListener(listener: DeferredObservableProperty.ValueChangeListener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: DeferredObservableProperty.ValueChangeListener<T>): Boolean = listeners.removeListener(listener)
}