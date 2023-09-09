package org.brightify.hyperdrive.internal

import org.brightify.hyperdrive.BaseObservableObject
import org.brightify.hyperdrive.property.MutableObservableProperty
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.property.impl.MutableObservablePropertyBoundProperty

internal class MutableObservablePropertyBoundPropertyProvider<OWNER: BaseObservableObject, T>(
    property: MutableObservableProperty<T>,
    equalityPolicy: ObservableProperty.EqualityPolicy<T>,
    localWillChange: (T) -> Unit,
    localDidChange: (T) -> Unit,
    boundWillChange: (T) -> Unit,
    boundDidChange: (T) -> Unit,
): MutableObservablePropertyProvider<OWNER, T>(
    observablePropertyFactory = {
        MutableObservablePropertyBoundProperty(
            property,
            equalityPolicy,
            localWillChange,
            localDidChange,
            boundWillChange,
            boundDidChange,
        )
    }
)
