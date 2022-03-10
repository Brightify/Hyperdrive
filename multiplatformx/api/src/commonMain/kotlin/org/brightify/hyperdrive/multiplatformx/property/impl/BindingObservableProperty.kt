package org.brightify.hyperdrive.multiplatformx.property.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.Lifecycle
import org.brightify.hyperdrive.multiplatformx.property.MutableObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.defaultEqualityPolicy

internal class BindingObservableProperty<T, U>(
    initialValue: T,
    private val boundFlow: Flow<U>,
    lifecycle: Lifecycle,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    private val readMapping: (U) -> T,
    private val setter: (T) -> Unit,
): MutableObservableProperty<T> {

    private var valueStorage: T = initialValue
    override var value: T
        get() = valueStorage
        set(newValue) {
            if (equalityPolicy.isEqual(value, newValue)) { return }
            listeners.runNotifyingListeners(newValue) {
                valueStorage = it
                setter(it)
            }
        }

    private val listeners = ValueChangeListenerHandler(this)

    init {
        lifecycle.whileAttached {
            // TODO: We want to ignore changes we made. Equality policy should be enough, but we better do it smarter.
            boundFlow.collect { newValue ->
                val mappedNewValue = readMapping(newValue)
                if (equalityPolicy.isEqual(value, mappedNewValue)) { return@collect }
                listeners.runNotifyingListeners(mappedNewValue) {
                    valueStorage = it
                }
            }
        }
    }

    override fun addListener(listener: ObservableProperty.Listener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<T>) = listeners.removeListener(listener)
}

