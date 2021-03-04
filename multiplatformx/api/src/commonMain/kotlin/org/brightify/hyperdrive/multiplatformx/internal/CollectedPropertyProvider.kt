package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal class CollectedPropertyProvider<OWNER, T>(
    private val owner: BaseViewModel,
    private val initialValue: T,
    private val flow: Flow<T>,
): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, T>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadOnlyProperty<OWNER, T> {
        val observer = owner.getPropertyObserver(property, initialValue)

        owner.lifecycle.whileAttached {
            flow.collect { newValue ->
                if (newValue != observer.value) {
                    owner.internalNotifyObjectWillChange()
                    observer.value = newValue
                }
            }
        }

        return MutableStateFlowBackedProperty(owner, observer)
    }
}