package org.brightify.hyperdrive.multiplatformx.property

public fun <T> defaultEqualityPolicy(): ObservableProperty.EqualityPolicy<T> = ObservableProperty.EqualityPolicy { oldValue, newValue ->
    oldValue == newValue
}

public fun <T> neverEqualPolicy(): ObservableProperty.EqualityPolicy<T> = ObservableProperty.EqualityPolicy { _, _ ->
    false
}

public fun <T> identityEqualityPolicy(): ObservableProperty.EqualityPolicy<T> = ObservableProperty.EqualityPolicy { oldValue, newValue ->
    oldValue === newValue
}