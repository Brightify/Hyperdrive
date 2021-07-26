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

internal class ManagedPropertyFlowListProvider<OWNER: BaseViewModel, VM: ManageableViewModel?>(
    private val initialChild: List<VM>,
    private val viewModelFlow: Flow<List<VM>>,
    private val publishedChanges: Boolean,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<List<VM>>,
): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, List<VM>>> {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadOnlyProperty<OWNER, List<VM>> {
        return CollectedViewModelProperty(viewModelFlow, thisRef.lifecycle, equalityPolicy, initialChild)
            .also { thisRef.registerViewModelProperty(property, it) }
            .also { ManagedPropertyListHandler(thisRef, it, publishedChanges) }
            .toKotlinProperty()
    }
}
