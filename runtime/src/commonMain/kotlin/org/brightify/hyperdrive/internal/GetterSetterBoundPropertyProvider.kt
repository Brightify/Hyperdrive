package org.brightify.hyperdrive.internal

import org.brightify.hyperdrive.BaseObservableObject
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.property.impl.GetterSetterBoundProperty

internal class GetterSetterBoundPropertyProvider<OWNER: BaseObservableObject, T>(
    private val getter: () -> T,
    private val setter: (T) -> Unit,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T>,
): MutableObservablePropertyProvider<OWNER, T>(
    observablePropertyFactory = {
        GetterSetterBoundProperty(equalityPolicy, getter, setter)
    }
)
