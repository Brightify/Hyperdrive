package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseObservableObject
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.ValueObservableProperty

internal class PublishedPropertyProvider<OWNER: BaseObservableObject, T>(
    initialValue: T,
    equalityPolicy: ObservableProperty.EqualityPolicy<T>,
): MutableObservablePropertyProvider<OWNER, T>(
    observablePropertyFactory = { _ ->
        ValueObservableProperty(initialValue, equalityPolicy)
    }
)

