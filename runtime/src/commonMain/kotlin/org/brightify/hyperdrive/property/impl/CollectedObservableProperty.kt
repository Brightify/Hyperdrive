package org.brightify.hyperdrive.property.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.Lifecycle
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.property.defaultEqualityPolicy

internal class CollectedObservableProperty<T>(
    private val flow: Flow<T>,
    private val lifecycle: Lifecycle,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    initialValue: T,
): ObservableProperty<T> {
    override var value: T = initialValue
        private set

    private val listeners = ValueChangeListenerHandler(this)

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

    override fun addListener(listener: ObservableProperty.Listener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<T>) = listeners.removeListener(listener)
}
