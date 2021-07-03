package org.brightify.hyperdrive.multiplatformx.internal.list

import org.brightify.hyperdrive.multiplatformx.ManageableViewModel

internal class MutableListProxy<T>(
    private val objectWillChangeTrigger: ManageableViewModel.ObjectWillChangeTrigger,
    val mutableList: MutableList<T>
): MutableList<T>, List<T> by mutableList {
    private inline fun <T> notifying(perform: () -> T): T {
        objectWillChangeTrigger.notifyObjectWillChange()
        return perform()
    }

    override fun iterator(): MutableIterator<T> {
        return MutableIteratorProxy(objectWillChangeTrigger, mutableList.iterator())
    }

    override fun listIterator(): MutableListIterator<T> {
        return MutableListIteratorProxy(objectWillChangeTrigger, mutableList.listIterator())
    }

    override fun listIterator(index: Int): MutableListIterator<T> {
        return MutableListIteratorProxy(objectWillChangeTrigger, mutableList.listIterator(index))
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
        return MutableListProxy(objectWillChangeTrigger, mutableList.subList(fromIndex, toIndex))
    }

    override fun add(element: T): Boolean = notifying {
        mutableList.add(element)
    }

    override fun add(index: Int, element: T) = notifying {
        mutableList.add(index, element)
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean = notifying {
        mutableList.addAll(index, elements)
    }

    override fun addAll(elements: Collection<T>): Boolean = notifying {
        mutableList.addAll(elements)
    }

    override fun clear() = notifying {
        mutableList.clear()
    }

    override fun remove(element: T): Boolean = notifying {
        mutableList.remove(element)
    }

    override fun removeAll(elements: Collection<T>): Boolean = notifying {
        mutableList.removeAll(elements)
    }

    override fun removeAt(index: Int): T = notifying {
        mutableList.removeAt(index)
    }

    override fun retainAll(elements: Collection<T>): Boolean = notifying {
        mutableList.retainAll(elements)
    }

    override fun set(index: Int, element: T): T = notifying {
        mutableList.set(index, element)
    }
}