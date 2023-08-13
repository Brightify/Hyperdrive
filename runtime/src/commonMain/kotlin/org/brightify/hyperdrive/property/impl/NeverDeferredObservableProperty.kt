package org.brightify.hyperdrive.property.impl

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.property.DeferredObservableProperty
import org.brightify.hyperdrive.utils.Optional

internal class NeverDeferredObservableProperty<T>: DeferredObservableProperty<T> {
    override val latestValue: Optional<T> = Optional.None

    private val listeners = ValueChangeListenerHandler(this)

    override suspend fun await(): T = coroutineScope {
        awaitCancellation()
    }

    override suspend fun nextValue(): T = coroutineScope {
        awaitCancellation()
    }

    override fun addListener(listener: DeferredObservableProperty.Listener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: DeferredObservableProperty.Listener<T>) = listeners.removeListener(listener)
}
