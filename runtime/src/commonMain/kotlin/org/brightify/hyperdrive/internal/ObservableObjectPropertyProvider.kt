package org.brightify.hyperdrive.internal

import org.brightify.hyperdrive.BaseObservableObject
import org.brightify.hyperdrive.ObservableObject
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.property.impl.ObservableObjectObservableProperty

internal class ObservableObjectPropertyProvider<OWNER: BaseObservableObject, T: ObservableObject>(
    initialValue: T,
    equalityPolicy: ObservableProperty.EqualityPolicy<T>,
): MutableObservablePropertyProvider<OWNER, T>(
    observablePropertyFactory = {
        ObservableObjectObservableProperty(initialValue, equalityPolicy)
    }
)
