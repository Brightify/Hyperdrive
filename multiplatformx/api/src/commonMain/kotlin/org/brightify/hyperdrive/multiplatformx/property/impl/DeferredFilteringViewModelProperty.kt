package org.brightify.hyperdrive.multiplatformx.property.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.DeferredViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty

internal class DeferredFilteringViewModelProperty<T>(
    private val filtered: ViewModelProperty<T>,
    private val predicate: (T) -> Boolean,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<T>,
): DeferredViewModelProperty<T>, ViewModelProperty.ValueChangeListener<T> {
    override val latestValue: T?
        get() = storage.value
    private var pendingValue: T? = null

    private val listeners = DeferredViewModelPropertyListeners(this)
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

    override fun valueWillChange(newValue: T) {
        if (!predicate(newValue)) { return }

        val oldValue = storage.value
        val shouldSave = oldValue == null || equalityPolicy.isEqual(oldValue, newValue)
        if (shouldSave) {
            pendingValue = newValue
            listeners.notifyValueWillChange(newValue)
        }
    }

    override fun valueDidChange(oldValue: T) {
        val oldFilteredValue = storage.value
        storage.value = pendingValue ?: return
        pendingValue = null
        listeners.notifyValueDidChange(oldFilteredValue)
    }

    override fun addListener(listener: DeferredViewModelProperty.ValueChangeListener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: DeferredViewModelProperty.ValueChangeListener<T>): Boolean = listeners.removeListener(listener)
}