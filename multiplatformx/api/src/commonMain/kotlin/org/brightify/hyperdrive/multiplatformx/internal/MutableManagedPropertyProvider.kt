package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.MutableViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.toKotlinMutableProperty
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class MutableManagedPropertyProvider<OWNER: BaseViewModel, VM: ManageableViewModel?>(
    private val managedProperty: MutableViewModelProperty<VM>,
    private val publishedChanges: Boolean,
): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, VM>> {

    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, VM> {
        return managedProperty
            .also { thisRef.registerViewModelProperty(property, it) }
            .also { ManagedPropertyHandler(thisRef, it, publishedChanges) }
            .toKotlinMutableProperty()
    }
}