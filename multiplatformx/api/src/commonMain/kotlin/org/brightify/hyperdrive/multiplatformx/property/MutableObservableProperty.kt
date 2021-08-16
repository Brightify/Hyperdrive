package org.brightify.hyperdrive.multiplatformx.property

public interface MutableObservableProperty<T>: ObservableProperty<T> {
    public override var value: T
}