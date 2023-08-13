package org.brightify.hyperdrive.property

/**
 * Create an equality policy using the default [T.equals] method.
 */
public fun <T> defaultEqualityPolicy(): ObservableProperty.EqualityPolicy<T> = ObservableProperty.EqualityPolicy { oldValue, newValue ->
    oldValue == newValue
}

/**
 * Create an equality policy that always returns false (values are never equal).
 *
 * Use this policy if you need to turn off the default `distinctUntilChanged` behavior.
 */
public fun <T> neverEqualPolicy(): ObservableProperty.EqualityPolicy<T> = ObservableProperty.EqualityPolicy { _, _ ->
    false
}

/**
 * Create an equality policy that returns true only if the values are identical, not just equal.
 */
public fun <T> identityEqualityPolicy(): ObservableProperty.EqualityPolicy<T> = ObservableProperty.EqualityPolicy { oldValue, newValue ->
    oldValue === newValue
}
