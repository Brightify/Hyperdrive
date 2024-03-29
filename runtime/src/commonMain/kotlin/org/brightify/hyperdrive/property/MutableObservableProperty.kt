package org.brightify.hyperdrive.property

import org.brightify.hyperdrive.property.impl.ValueObservableProperty

/**
 * [ObservableProperty] with the added functionality of a mutable value.
 */
public interface MutableObservableProperty<T>: ObservableProperty<T> {
    public override var value: T
}

@Suppress("FunctionName")
public fun <T> MutableObservableProperty(
    initialValue: T,
    equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
): MutableObservableProperty<T> {
    return ValueObservableProperty(initialValue, equalityPolicy)
}
