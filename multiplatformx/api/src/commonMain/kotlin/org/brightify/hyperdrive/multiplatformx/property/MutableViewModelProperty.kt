package org.brightify.hyperdrive.multiplatformx.property

public interface MutableViewModelProperty<T>: ViewModelProperty<T> {
    public override var value: T
}