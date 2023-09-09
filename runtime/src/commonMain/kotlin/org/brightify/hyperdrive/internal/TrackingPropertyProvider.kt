package org.brightify.hyperdrive.internal

import org.brightify.hyperdrive.BaseObservableObject
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.property.impl.TrackingProperty

internal class TrackingPropertyProvider<OWNER: BaseObservableObject, T, P: ObservableProperty<T>, U>(
    property: P,
    equalityPolicy: ObservableProperty.EqualityPolicy<U>,
    read: P.(T) -> U,
    write: P.(U) -> Unit,
): MutableObservablePropertyProvider<OWNER, U>(
    observablePropertyFactory = {
        TrackingProperty(property, equalityPolicy, read, write)
    }
)
