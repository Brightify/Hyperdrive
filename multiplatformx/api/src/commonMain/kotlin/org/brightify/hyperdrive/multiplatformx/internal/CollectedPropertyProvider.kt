package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.CollectedViewModelProperty

internal class CollectedPropertyProvider<OWNER: BaseViewModel, T>(
    private val initialValue: T,
    private val flow: Flow<T>,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<T>,
): ViewModelPropertyProvider<OWNER, T>(
    viewModelPropertyFactory = { owner ->
        CollectedViewModelProperty(flow, owner.lifecycle, equalityPolicy, initialValue)
    }
)
