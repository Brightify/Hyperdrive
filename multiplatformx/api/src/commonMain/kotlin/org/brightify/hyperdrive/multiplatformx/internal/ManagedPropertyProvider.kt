package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.toKotlinProperty
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal class ManagedPropertyProvider<OWNER: BaseViewModel, VM: ManageableViewModel?>(
    private val publishedChanges: Boolean,
    private val managedPropertyFactory: (owner: OWNER) -> ViewModelProperty<VM>,
): ViewModelPropertyProvider<OWNER, VM>(
    viewModelPropertyFactory = { owner ->
        managedPropertyFactory(owner)
            .also { ManagedPropertyHandler(owner, it, publishedChanges) }
    }
)
