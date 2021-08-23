package org.brightify.hyperdrive.multiplatformx.property

/**
 * [ObservableProperty] with the added functionality of a mutable value.
 */
public interface MutableObservableProperty<T>: ObservableProperty<T> {
    public override var value: T
}
