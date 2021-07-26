package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.impl.ValueViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.toKotlinMutableProperty
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class ManagedPropertyProvider<OWNER: BaseViewModel, VM: ManageableViewModel?>(
    private val initialChild: VM,
    private val publishedChanges: Boolean,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<VM>,
): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, VM>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, VM> {
        return ValueViewModelProperty(initialChild, equalityPolicy)
            .also { thisRef.registerViewModelProperty(property, it) }
            .also { ManagedPropertyHandler(thisRef, it, publishedChanges) }
            .toKotlinMutableProperty()
    }
}
