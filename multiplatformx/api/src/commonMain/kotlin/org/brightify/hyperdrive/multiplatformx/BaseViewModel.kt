package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

abstract class BaseViewModel {
    private val propertyObservers = mutableMapOf<String, MutableStateFlow<*>>()

    private val objectWillChangeTrigger = BroadcastChannel<Unit>(Channel.CONFLATED)
    val observeObjectWillChange: Flow<Unit> = objectWillChangeTrigger.asFlow()
    @Suppress("unused")
    val observeObjectWillChangeWrapper = NonNullFlowWrapper(observeObjectWillChange)

    val lifecycle = Lifecycle()

    init {
        lifecycle.whileAttached {
            whileAttached()
        }
    }

    private fun <T> getPropertyObserver(property: KProperty<*>, initialValue: T): MutableStateFlow<T> {
        return propertyObservers.getOrPut(property.name) {
            MutableStateFlow(initialValue)
        } as MutableStateFlow<T>
    }

    fun <T> observe(property: KProperty0<T>): Lazy<StateFlow<T>> = lazy { getPropertyObserver(property, property.get()) }

    protected open suspend fun whileAttached() { }

    protected fun <OWNER, T> published(initialValue: T): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        return PublishedPropertyProvider(initialValue)
    }

    protected fun <OWNER, T> published(initialValue: List<T>): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, MutableList<T>>> {
        return PublishedListPropertyProvider(initialValue)
    }

    protected fun <OWNER, T> collected(stateFlow: StateFlow<T>): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, T>> {
        return CollectedPropertyProvider(stateFlow.value, stateFlow)
    }

    protected fun <OWNER, T, U> collected(stateFlow: StateFlow<T>, mapping: (T) -> U): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> {
        return CollectedPropertyProvider(mapping(stateFlow.value), stateFlow.map { mapping(it) })
    }

    protected fun <OWNER, T> collected(initialValue: T, flow: Flow<T>): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, T>> {
        return CollectedPropertyProvider(initialValue, flow)
    }

    protected fun <OWNER, T, U> collected(initialValue: T, flow: Flow<T>, mapping: (T) -> U): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, T>> {
        return CollectedPropertyProvider(initialValue, flow)
    }

    protected fun <OWNER, T: BaseViewModel?> managed(childModel: T): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        return ManagedPropertyProvider(childModel)
    }

    // TODO: Return a list proxy that will trigger the `objectWillLoad` every time its contents are changed and also that keeps each item managed.
    // protected fun <OWNER, T: BaseInterfaceModel> managed(childModels: List<T>): ReadWriteProperty<OWNER, MutableList<T>> {
    // }

    protected fun notifyObjectWillChange() {
        objectWillChangeTrigger.offer(Unit)
    }

    private inner class PublishedPropertyProvider<OWNER, T>(private val initialValue: T): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, T> {
            val observer = getPropertyObserver(property, initialValue)

            return object: ReadWriteProperty<OWNER, T> {
                override fun getValue(thisRef: OWNER, property: KProperty<*>): T {
                    return observer.value
                }

                override fun setValue(thisRef: OWNER, property: KProperty<*>, value: T) {
                    notifyObjectWillChange()

                    observer.value = value
                }
            }
        }
    }

    private inner class PublishedListPropertyProvider<OWNER, T>(private val initialValue: List<T>): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, MutableList<T>>> {
        override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, MutableList<T>> {
            val observer = getPropertyObserver<MutableList<T>>(property, MutableListProxy(initialValue.toMutableList()))

            return object: MutableStateFlowBackedProperty<OWNER, MutableList<T>>(observer) {
                override fun setValue(thisRef: OWNER, property: KProperty<*>, value: MutableList<T>) {
                    super.setValue(thisRef, property, MutableListProxy(value))
                }
            }
        }

        private inner class MutableIteratorProxy<T>(private val iterator: MutableIterator<T>): MutableIterator<T>, Iterator<T> by iterator {
            override fun remove() {
                notifyObjectWillChange()
                iterator.remove()
            }
        }

        private inner class MutableListIteratorProxy<T>(private val iterator: MutableListIterator<T>): MutableListIterator<T>, ListIterator<T> by iterator {
            private inline fun <T> notifying(perform: () -> T): T {
                notifyObjectWillChange()
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

        private inner class MutableListProxy<T>(val mutableList: MutableList<T>): MutableList<T>, List<T> by mutableList {
            private inline fun <T> notifying(perform: () -> T): T {
                notifyObjectWillChange()
                return perform()
            }

            override fun iterator(): MutableIterator<T> {
                return MutableIteratorProxy(mutableList.iterator())
            }

            override fun listIterator(): MutableListIterator<T> {
                return MutableListIteratorProxy(mutableList.listIterator())
            }

            override fun listIterator(index: Int): MutableListIterator<T> {
                return MutableListIteratorProxy(mutableList.listIterator(index))
            }

            override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
                return MutableListProxy(mutableList.subList(fromIndex, toIndex))
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
    }

    private inner class CollectedPropertyProvider<OWNER, T>(
        private val initialValue: T,
        private val flow: Flow<T>,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, T>> {
        override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadOnlyProperty<OWNER, T> {
            val observer = getPropertyObserver(property, initialValue)

            lifecycle.whileAttached {
                flow.collect {
                    notifyObjectWillChange()
                    observer.value = it
                }
            }

            return MutableStateFlowBackedProperty(observer)
        }
    }

    private inner class ManagedPropertyProvider<OWNER, T: BaseViewModel?>(private val initialChild: T): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, T> {
            val child = getPropertyObserver(property, initialChild)

            lifecycle.whileAttached {
                val previousChild = child.map {
                    // This cast is not useless, without it we can't emit null in the `onStart` operator.
                    @Suppress("USELESS_CAST")
                    it as T?
                }.onStart { emit(null) }
                previousChild.zip(child) { oldChild, newChild ->
                    oldChild to newChild
                }.collect {
                    val (oldChild, newChild) = it
                    if (oldChild != null) {
                        lifecycle.removeChild(oldChild.lifecycle)
                    }

                    if (newChild != null) {
                        lifecycle.addChild(newChild.lifecycle)
                    }
                }
            }

            lifecycle.whileAttached {
                child.flatMapLatest { it?.observeObjectWillChange ?: emptyFlow() }.collect {
                    notifyObjectWillChange()
                }
            }

            return MutableStateFlowBackedProperty(child)
        }
    }

    private open inner class MutableStateFlowBackedProperty<OWNER, T>(
        private val stateFlow: MutableStateFlow<T>
    ): ReadWriteProperty<OWNER, T> {

        override fun getValue(thisRef: OWNER, property: KProperty<*>): T {
            return stateFlow.value
        }

        override fun setValue(thisRef: OWNER, property: KProperty<*>, value: T) {
            notifyObjectWillChange()
            stateFlow.value = value
        }
    }
}
