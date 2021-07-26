package org.brightify.hyperdrive.multiplatformx.property

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.impl.MappingViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.SwitchMappingViewModelProperty
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

public interface ViewModelProperty<T> {
    public val value: T

    public fun addListener(listener: ValueChangeListener<T>): CancellationToken

    public fun removeListener(listener: ValueChangeListener<T>): Boolean

    public operator fun <OWNER> getValue(thisRef: OWNER, property: KProperty<*>): T = value

    public interface ValueChangeListener<T> {
        public fun valueWillChange(newValue: T) { }

        public fun valueDidChange(oldValue: T) { }
    }

    public fun interface EqualityPolicy<T> {
        public fun isEqual(oldValue: T, newValue: T): Boolean
    }
}

public fun <T> defaultEqualityPolicy(): ViewModelProperty.EqualityPolicy<T> = ViewModelProperty.EqualityPolicy { oldValue, newValue ->
    oldValue == newValue
}

public fun <T> neverEqualPolicy(): ViewModelProperty.EqualityPolicy<T> = ViewModelProperty.EqualityPolicy { _, _ ->
    false
}

public fun <T> identityEqualityPolicy(): ViewModelProperty.EqualityPolicy<T> = ViewModelProperty.EqualityPolicy { oldValue, newValue ->
    oldValue === newValue
}
public fun <T, U> ViewModelProperty<T>.map(
    equalityPolicy: ViewModelProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
    transform: (T) -> U
): ViewModelProperty<U> {
    return MappingViewModelProperty(this, transform, equalityPolicy)
}

public fun <T, U> ViewModelProperty<T>.switchMap(
    equalityPolicy: ViewModelProperty.EqualityPolicy<ViewModelProperty<U>> = identityEqualityPolicy(),
    transform: (T) -> ViewModelProperty<U>,
): ViewModelProperty<U> {
    return SwitchMappingViewModelProperty(this, transform, equalityPolicy)
}

internal fun <OWNER, T> ViewModelProperty<T>.toKotlinProperty(): ReadOnlyProperty<OWNER, T> = ReadOnlyProperty { _, _ ->
    this@toKotlinProperty.value
}

internal fun <OWNER, T> MutableViewModelProperty<T>.toKotlinMutableProperty(): ReadWriteProperty<OWNER, T> =
    object: ReadWriteProperty<OWNER, T> {
        override fun getValue(thisRef: OWNER, property: KProperty<*>): T = this@toKotlinMutableProperty.value

        override fun setValue(thisRef: OWNER, property: KProperty<*>, value: T) {
            this@toKotlinMutableProperty.value = value
        }
    }

