package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.property.impl.BindingViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty

internal class BoundPropertyProvider<OWNER: BaseViewModel, T, U>(
    private val initialValue: T,
    private val flow: Flow<U>,
    private val readMapping: (U) -> T,
    private val setter: (T) -> Unit,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<T>,
): MutableViewModelPropertyProvider<OWNER, T>(
    viewModelPropertyFactory = { owner ->
        BindingViewModelProperty(initialValue, flow, owner.lifecycle, equalityPolicy, readMapping, setter)
    }
)
