package org.brightify.hyperdrive.multiplatformx.property.impl

import kotlinx.coroutines.awaitCancellation
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.DeferredObservableProperty
import org.brightify.hyperdrive.utils.Optional

internal class NeverDeferredObservableProperty<T>: DeferredObservableProperty<T> {
    override val latestValue: Optional<T> = Optional.None

    private val listeners = DeferredObservablePropertyListeners(this)

    override suspend fun await(): T {
        awaitCancellation()
    }

    override suspend fun nextValue(): T {
        awaitCancellation()
    }

    override fun addListener(listener: DeferredObservableProperty.ValueChangeListener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: DeferredObservableProperty.ValueChangeListener<T>): Boolean = listeners.removeListener(listener)
}