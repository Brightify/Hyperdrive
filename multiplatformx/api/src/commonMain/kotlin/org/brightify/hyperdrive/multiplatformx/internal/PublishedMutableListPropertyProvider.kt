package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.internal.list.MutableListProxy
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

