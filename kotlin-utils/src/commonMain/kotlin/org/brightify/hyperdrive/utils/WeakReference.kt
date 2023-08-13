package org.brightify.hyperdrive.utils

public expect class WeakReference<T: Any>(referred: T) {
    public fun get(): T?

    public fun clear()
}