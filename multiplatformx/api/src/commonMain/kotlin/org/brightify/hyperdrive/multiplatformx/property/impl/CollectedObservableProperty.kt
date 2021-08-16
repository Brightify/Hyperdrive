package org.brightify.hyperdrive.multiplatformx.property.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.Lifecycle
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.defaultEqualityPolicy

internal class CollectedObservableProperty<T>(
    private val flow: Flow<T>,
    private val lifecycle: Lifecycle,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    initialValue: T,
): ObservableProperty<T> {
    override var value: T = initialValue
        private set

    private val listeners = ObservablePropertyListeners(this)

    init {
        lifecycle.whileAttached {
            flow.collect { newValue ->
                if (equalityPolicy.isEqual(value, newValue)) { return@collect }
                listeners.runNotifyingListeners(newValue) {
                    value = it
                }
            }
        }
    }

    override fun addListener(listener: ObservableProperty.ValueChangeListener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.ValueChangeListener<T>): Boolean = listeners.removeListener(listener)
}