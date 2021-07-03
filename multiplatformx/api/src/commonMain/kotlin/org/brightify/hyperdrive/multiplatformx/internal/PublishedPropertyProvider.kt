package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class PublishedPropertyProvider<OWNER, T>(
    private val owner: BaseViewModel,
    private val objectWillChangeTrigger: ManageableViewModel.ObjectWillChangeTrigger,
    private val initialValue: T,
): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, T> {
        val observer = owner.getPropertyObserver(property, initialValue)

        return object: ReadWriteProperty<OWNER, T> {
            override fun getValue(thisRef: OWNER, property: KProperty<*>): T {
                return observer.value
            }

            override fun setValue(thisRef: OWNER, property: KProperty<*>, value: T) {
                objectWillChangeTrigger.notifyObjectWillChange()

                observer.value = value
            }
        }
    }
}