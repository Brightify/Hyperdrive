package org.brightify.hyperdrive.utils

public actual class AtomicReference<T> actual constructor(
    public actual var value: T
) {

    public actual fun compareAndSet(expected: T, new: T): Boolean {
        return if (value == expected) {
            value = new
            true
        } else {
            false
        }
    }

    public actual fun compareAndSwap(expected: T, new: T): T {
        return if (value == expected) {
            val old = value
            value = new
            old
        } else {
            new
        }
    }
}
