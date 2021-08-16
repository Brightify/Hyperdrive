package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.multiplatformx.BaseObservableManageableObject
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.AsyncBindingObservableProperty
import org.brightify.hyperdrive.multiplatformx.util.AsyncQueue

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