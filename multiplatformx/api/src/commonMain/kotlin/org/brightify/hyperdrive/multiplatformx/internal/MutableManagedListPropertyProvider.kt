package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.MutableViewModelProperty

internal class MutableManagedListPropertyProvider<OWNER: BaseViewModel, VM: ManageableViewModel?>(
    private val publishedChanges: Boolean,
    private val managedPropertyFactory: (owner: OWNER) -> MutableViewModelProperty<List<VM>>,
): MutableViewModelPropertyProvider<OWNER, List<VM>>(
    viewModelPropertyFactory = { owner ->
        managedPropertyFactory(owner)
            .also { ManagedListPropertyHandler(owner, it, publishedChanges) }
    }
)
