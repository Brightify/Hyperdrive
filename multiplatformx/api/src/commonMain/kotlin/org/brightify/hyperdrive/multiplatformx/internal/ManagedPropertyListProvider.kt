package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.impl.ValueViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.toKotlinMutableProperty
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class ManagedPropertyListProvider<OWNER: BaseViewModel, VM: ManageableViewModel?>(
    private val changeTrackingTrigger: ManageableViewModel.ChangeTrackingTrigger,
    private val initialChild: List<VM>,
    private val publishedChanges: Boolean,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<List<VM>>,
): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, List<VM>>> {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, List<VM>> {
        return ValueViewModelProperty(initialChild, equalityPolicy)
            .also { thisRef.registerViewModelProperty(property, it) }
            .also { ManagedPropertyListHandler(thisRef, it, publishedChanges) }
            .toKotlinMutableProperty()
    }
}
