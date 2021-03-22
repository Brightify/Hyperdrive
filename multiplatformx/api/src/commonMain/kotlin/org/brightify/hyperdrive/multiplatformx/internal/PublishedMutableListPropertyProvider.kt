package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class PublishedMutableListPropertyProvider<OWNER, T>(
    private val owner: BaseViewModel,
    private val initialValue: MutableList<T>,
): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, MutableList<T>>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, MutableList<T>> {
        val observer = owner.getPropertyObserver<MutableList<T>>(property, MutableListProxy(owner, initialValue))

        return object: MutableStateFlowBackedProperty<OWNER, MutableList<T>>(owner, observer) {
            override fun setValue(thisRef: OWNER, property: KProperty<*>, value: MutableList<T>) {
                val wrappedList = MutableListProxy(owner, value)
                super.setValue(thisRef, property, wrappedList)
            }
        }
    }
}

private class MutableIteratorProxy<T>(
    private val owner: BaseViewModel,
    private val iterator: MutableIterator<T>,
): MutableIterator<T>, Iterator<T> by iterator {
    override fun remove() {
        owner.internalNotifyObjectWillChange()
        iterator.remove()
    }
}

private class MutableListIteratorProxy<T>(
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

private class MutableListProxy<T>(
    private val owner: BaseViewModel,
    val mutableList: MutableList<T>
): MutableList<T>, List<T> by mutableList {
    private inline fun <T> notifying(perform: () -> T): T {
        owner.internalNotifyObjectWillChange()
        return perform()
    }

    override fun iterator(): MutableIterator<T> {
        return MutableIteratorProxy(owner, mutableList.iterator())
    }

    override fun listIterator(): MutableListIterator<T> {
        return MutableListIteratorProxy(owner, mutableList.listIterator())
    }

    override fun listIterator(index: Int): MutableListIterator<T> {
        return MutableListIteratorProxy(owner, mutableList.listIterator(index))
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
        return MutableListProxy(owner, mutableList.subList(fromIndex, toIndex))
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

