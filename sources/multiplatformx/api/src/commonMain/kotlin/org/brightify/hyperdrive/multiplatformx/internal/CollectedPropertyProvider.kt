package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.multiplatformx.BaseObservableManageableObject
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.CollectedObservableProperty

internal class CollectedPropertyProvider<OWNER: BaseObservableManageableObject, T>(
    private val initialValue: T,
    private val flow: Flow<T>,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T>,
): ObservablePropertyProvider<OWNER, T>(
    observablePropertyFactory = { owner ->
        CollectedObservableProperty(flow, owner.lifecycle, equalityPolicy, initialValue)
    }
)
