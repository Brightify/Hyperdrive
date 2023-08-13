package org.brightify.hyperdrive.property.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.Lifecycle
import org.brightify.hyperdrive.property.MutableObservableProperty
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.property.defaultEqualityPolicy
import org.brightify.hyperdrive.util.AsyncQueue

internal class AsyncBindingObservableProperty<T, U>(
    initialValue: T,
    private val boundFlow: Flow<U>,
    lifecycle: Lifecycle,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    overflowPolicy: AsyncQueue.OverflowPolicy = AsyncQueue.OverflowPolicy.Conflate,
    private val readMapping: (U) -> T,
    asyncSetter: suspend (T) -> Unit,
): MutableObservableProperty<T> {

    private var valueStorage: T = initialValue
    override var value: T
        get() = valueStorage
        set(newValue) {
            if (equalityPolicy.isEqual(value, newValue)) { return }
            listeners.runNotifyingListeners(newValue) {
                valueStorage = it
                queue.push(it)
            }
        }

    private val listeners = ValueChangeListenerHandler(this)
    private val queue = AsyncQueue(action = asyncSetter, overflowPolicy = overflowPolicy, lifecycle = lifecycle)

    init {
        lifecycle.whileAttached {
            // TODO: We want to ignore changes we made. Equality policy should be enough, but we better do it smarter.
            boundFlow
                .collectLatest { newValue ->
                    queue.awaitIdle()
                    val mappedNewValue = readMapping(newValue)
                    if (equalityPolicy.isEqual(value, mappedNewValue)) { return@collectLatest }
                    listeners.runNotifyingListeners(mappedNewValue) {
                        valueStorage = it
                    }
                }
        }
    }

    override fun addListener(listener: ObservableProperty.Listener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<T>) = listeners.removeListener(listener)
}
