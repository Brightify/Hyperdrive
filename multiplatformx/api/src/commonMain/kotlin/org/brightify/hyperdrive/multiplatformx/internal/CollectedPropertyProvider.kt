package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal class CollectedPropertyProvider<OWNER, T>(
    private val owner: BaseViewModel,
    private val objectWillChangeTrigger: ManageableViewModel.ObjectWillChangeTrigger,
    private val initialValue: T,
    private val flow: Flow<T>,
): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, T>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadOnlyProperty<OWNER, T> {
        val observer = owner.getPropertyObserver(property, initialValue)

        owner.lifecycle.whileAttached {
            flow.collect { newValue ->
                if (newValue != observer.value) {
                    objectWillChangeTrigger.notifyObjectWillChange()
                    observer.value = newValue
                }
            }
        }

        return MutableStateFlowBackedProperty(objectWillChangeTrigger, observer)
    }
}