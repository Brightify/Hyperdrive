package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseObservableObject
import org.brightify.hyperdrive.multiplatformx.ObservableObject
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.ObservableObjectObservableProperty

internal class ObservableObjectPropertyProvider<OWNER: BaseObservableObject, T: ObservableObject>(
    initialValue: T,
    equalityPolicy: ObservableProperty.EqualityPolicy<T>,
): MutableObservablePropertyProvider<OWNER, T>(
    observablePropertyFactory = {
        ObservableObjectObservableProperty(initialValue, equalityPolicy)
    }
)