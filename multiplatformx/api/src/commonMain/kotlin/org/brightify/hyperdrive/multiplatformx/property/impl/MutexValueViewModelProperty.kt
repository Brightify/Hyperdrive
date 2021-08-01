package org.brightify.hyperdrive.multiplatformx.property.impl

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty

internal class MutexValueViewModelProperty<T>(
    initialValue: T,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<T>,
): ViewModelProperty<T> {

    override var value: T = initialValue
        private set(newValue) {
            if (equalityPolicy.isEqual(field, newValue)) { return }
            // FIXME: This might be a deadlock-creating place because the value is updated inside a lock.
            listeners.runNotifyingListeners(newValue) {
                field = it
            }
        }
    private val mutationLock = Mutex()
    private val listeners = ViewModelPropertyListeners(this)

    override fun addListener(listener: ViewModelProperty.ValueChangeListener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ViewModelProperty.ValueChangeListener<T>): Boolean = listeners.removeListener(listener)

    suspend fun set(newValue: T): Unit = mutationLock.withLock {
        value = newValue
    }

    suspend fun compareAndSet(expect: T, update: T): Boolean = mutationLock.withLock {
        if (equalityPolicy.isEqual(value, expect)) {
            value = update
            true
        } else {
            false
        }
    }

}