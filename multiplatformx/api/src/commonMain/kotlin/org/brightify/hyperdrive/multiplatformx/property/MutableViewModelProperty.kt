package org.brightify.hyperdrive.multiplatformx.property

import kotlin.reflect.KProperty

public interface MutableViewModelProperty<T>: ViewModelProperty<T> {
    public override var value: T

    public operator fun <OWNER> setValue(thisRef: OWNER, property: KProperty<*>, value: T) {
        this.value = value
    }
}