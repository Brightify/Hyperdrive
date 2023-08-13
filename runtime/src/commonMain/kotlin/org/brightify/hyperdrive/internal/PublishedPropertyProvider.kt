package org.brightify.hyperdrive.internal

import org.brightify.hyperdrive.BaseObservableObject
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.property.impl.ValueObservableProperty

internal class PublishedPropertyProvider<OWNER: BaseObservableObject, T>(
    initialValue: T,
    equalityPolicy: ObservableProperty.EqualityPolicy<T>,
): MutableObservablePropertyProvider<OWNER, T>(
    observablePropertyFactory = {
        ValueObservableProperty(initialValue, equalityPolicy)
    }
)
