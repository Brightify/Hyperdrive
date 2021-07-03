package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.InterfaceLock
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class BoundPropertyProvider<OWNER, T>(
    private val objectWillChangeTrigger: ManageableViewModel.ObjectWillChangeTrigger,
    private val lock: InterfaceLock,
    private val getterProvider: PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, T>>,
    private val setter: suspend (T) -> Unit,
): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, T> {
        val getter = getterProvider.provideDelegate(thisRef, property)
        var temporaryValue: T? = null

        return object: ReadWriteProperty<OWNER, T> {
            override fun getValue(thisRef: OWNER, property: KProperty<*>): T = temporaryValue ?: getter.getValue(thisRef, property)

            override fun setValue(thisRef: OWNER, property: KProperty<*>, value: T) {
                if (lock.isLocked) {
                    return
                }
                objectWillChangeTrigger.notifyObjectWillChange()
                temporaryValue = value

                lock.runExclusively {
                    setter(value)
                    temporaryValue = null
                }
            }
        }
    }
}
