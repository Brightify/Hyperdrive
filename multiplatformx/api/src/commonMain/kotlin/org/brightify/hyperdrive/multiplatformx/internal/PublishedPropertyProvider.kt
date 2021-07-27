package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.ValueViewModelProperty

internal class PublishedPropertyProvider<OWNER: BaseViewModel, T>(
    initialValue: T,
    equalityPolicy: ViewModelProperty.EqualityPolicy<T>,
): MutableViewModelPropertyProvider<OWNER, T>(
    viewModelPropertyFactory = { _ ->
        ValueViewModelProperty(initialValue, equalityPolicy)
    }
)

