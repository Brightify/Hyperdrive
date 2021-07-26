package org.brightify.hyperdrive.multiplatformx.property.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.Lifecycle
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.defaultEqualityPolicy

internal class CollectedViewModelProperty<T>(
    private val flow: Flow<T>,
    private val lifecycle: Lifecycle,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    initialValue: T,
): ViewModelProperty<T> {
    override var value: T = initialValue
        private set

    private val listeners = ViewModelPropertyListeners(this)

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

    override fun addListener(listener: ViewModelProperty.ValueChangeListener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ViewModelProperty.ValueChangeListener<T>): Boolean = listeners.removeListener(listener)
}