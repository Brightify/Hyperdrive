package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.property.impl.CollectedViewModelProperty
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.toKotlinProperty
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal class ManagedPropertyFlowProvider<OWNER: BaseViewModel, VM: ManageableViewModel?>(
    private val initialChild: VM,
    private val viewModelFlow: Flow<VM>,
    private val publishedChanges: Boolean,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<VM>,
): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, VM>> {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadOnlyProperty<OWNER, VM> {
        return CollectedViewModelProperty(viewModelFlow, thisRef.lifecycle, equalityPolicy, initialChild)
            .also { thisRef.registerViewModelProperty(property, it) }
            .also { ManagedPropertyHandler(thisRef, it, publishedChanges) }
            .toKotlinProperty()
    }
}