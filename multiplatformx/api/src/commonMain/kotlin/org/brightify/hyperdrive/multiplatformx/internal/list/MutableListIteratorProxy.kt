package org.brightify.hyperdrive.multiplatformx.internal.list

import org.brightify.hyperdrive.multiplatformx.BaseViewModel

internal class MutableListIteratorProxy<T>(
    private val owner: BaseViewModel,
    private val iterator: MutableListIterator<T>,
): MutableListIterator<T>, ListIterator<T> by iterator {
    private inline fun <T> notifying(perform: () -> T): T {
        owner.internalNotifyObjectWillChange()
        return perform()
    }

    override fun add(element: T) = notifying {
        iterator.add(element)
    }

    override fun remove() = notifying {
        iterator.remove()
    }

    override fun set(element: T) = notifying {
        iterator.set(element)
    }
}