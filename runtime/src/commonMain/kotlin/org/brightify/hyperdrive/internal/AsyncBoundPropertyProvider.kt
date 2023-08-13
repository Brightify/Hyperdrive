package org.brightify.hyperdrive.internal

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.BaseObservableManageableObject
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.property.impl.AsyncBindingObservableProperty
import org.brightify.hyperdrive.util.AsyncQueue

internal class AsyncBoundPropertyProvider<OWNER: BaseObservableManageableObject, T, U>(
    private val initialValue: T,
    private val flow: Flow<U>,
    private val readMapping: (U) -> T,
    private val asyncSetter: suspend (T) -> Unit,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T>,
    private val overflowPolicy: AsyncQueue.OverflowPolicy,
): MutableObservablePropertyProvider<OWNER, T>(
    observablePropertyFactory = { owner ->
        AsyncBindingObservableProperty(initialValue, flow, owner.lifecycle, equalityPolicy, overflowPolicy, readMapping, asyncSetter)
    }
)
