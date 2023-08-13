package org.brightify.hyperdrive.utils

public expect class AtomicReference<T>(value: T) {
    public var value: T

    public fun compareAndSet(expected: T, new: T): Boolean

    public fun compareAndSwap(expected: T, new: T): T
}