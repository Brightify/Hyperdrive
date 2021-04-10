package org.brightify.hyperdrive.multiplatformx.internal.list

import org.brightify.hyperdrive.multiplatformx.BaseViewModel

internal class MutableIteratorProxy<T>(
    private val owner: BaseViewModel,
    private val iterator: MutableIterator<T>,
): MutableIterator<T>, Iterator<T> by iterator {
    override fun remove() {
        owner.internalNotifyObjectWillChange()
        iterator.remove()
    }
}