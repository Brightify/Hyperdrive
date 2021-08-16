package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.multiplatformx.BaseObservableManageableObject
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.BindingObservableProperty

internal class BoundPropertyProvider<OWNER: BaseObservableManageableObject, T, U>(
    private val initialValue: T,
    private val flow: Flow<U>,
    private val readMapping: (U) -> T,
    private val setter: (T) -> Unit,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T>,
): MutableObservablePropertyProvider<OWNER, T>(
    observablePropertyFactory = { owner ->
        BindingObservableProperty(initialValue, flow, owner.lifecycle, equalityPolicy, readMapping, setter)
    }
)

