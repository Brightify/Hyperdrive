package org.brightify.hyperdrive.utils

import java.util.concurrent.atomic.AtomicReference as JAtomicReference

public actual class AtomicReference<T> actual constructor(value: T) {
    private val storage = JAtomicReference(value)

    public actual var value: T
        get() = storage.get()
        set(newValue) {
            storage.set(newValue)
        }

    public actual fun compareAndSet(expected: T, new: T): Boolean {
        return storage.compareAndSet(expected, new)
    }

    public actual fun compareAndSwap(expected: T, new: T): T {
        return storage.compareAndExchange(expected, new)
    }
}