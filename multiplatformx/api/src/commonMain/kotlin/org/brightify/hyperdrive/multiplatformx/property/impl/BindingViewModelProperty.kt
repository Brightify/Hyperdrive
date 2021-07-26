package org.brightify.hyperdrive.multiplatformx.property.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.Lifecycle
import org.brightify.hyperdrive.multiplatformx.property.MutableViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.defaultEqualityPolicy

internal class BindingViewModelProperty<T, U>(
    private val boundStateFlow: MutableStateFlow<U>,
    private val lifecycle: Lifecycle,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    private val readMapping: (U) -> T,
    private val writeMapping: (T) -> U,
): MutableViewModelProperty<T> {

    private var valueStorage: T = readMapping(boundStateFlow.value)
    override var value: T
        get() = valueStorage
        set(newValue) {
            if (equalityPolicy.isEqual(value, newValue)) { return }
            listeners.runNotifyingListeners(newValue) {
                valueStorage = it
                boundStateFlow.value = writeMapping(it)
            }
        }

    private val listeners = ViewModelPropertyListeners(this)

    init {
        lifecycle.whileAttached {
            // TODO: We want to ignore changes we made. Equality policy should be enough, but we better do it smarter.
            boundStateFlow.collect { newValue ->
                val mappedNewValue = readMapping(newValue)
                if (equalityPolicy.isEqual(value, mappedNewValue)) { return@collect }
                listeners.runNotifyingListeners(mappedNewValue) {
                    valueStorage = it
                }
            }
        }
    }

    override fun addListener(listener: ViewModelProperty.ValueChangeListener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ViewModelProperty.ValueChangeListener<T>): Boolean = listeners.removeListener(listener)
}