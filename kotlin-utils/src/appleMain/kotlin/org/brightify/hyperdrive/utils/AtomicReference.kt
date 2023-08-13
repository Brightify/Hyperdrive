package org.brightify.hyperdrive.utils

import kotlin.concurrent.AtomicReference as KotlinAtomicReference

public actual class AtomicReference<T> actual constructor(
    value: T
) {
    private val backingReference = KotlinAtomicReference(value)

    public actual var value: T
        get() = backingReference.value
        set(value) {
            backingReference.value = value
        }

    public actual fun compareAndSet(expected: T, new: T): Boolean {
        return backingReference.compareAndSet(expected, new)
    }

    public actual fun compareAndSwap(expected: T, new: T): T {
        return backingReference.compareAndExchange(expected, new)
    }

}
