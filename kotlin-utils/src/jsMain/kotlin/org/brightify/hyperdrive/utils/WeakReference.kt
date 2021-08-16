package org.brightify.hyperdrive.utils

public actual class WeakReference<T: Any> actual constructor(referred: T) {
    private var value: T? = referred

    public actual fun get(): T? = value

    public actual fun clear() {
        value = null
    }
}