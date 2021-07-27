package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.toKotlinProperty
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal class ManagedPropertyProvider<OWNER: BaseViewModel, VM: ManageableViewModel?>(
    private val managedProperty: ViewModelProperty<VM>,
    private val publishedChanges: Boolean,
): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, VM>> {

    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadOnlyProperty<OWNER, VM> {
        return managedProperty
            .also { thisRef.registerViewModelProperty(property, it) }
            .also { ManagedPropertyHandler(thisRef, it, publishedChanges) }
            .toKotlinProperty()
    }
}
