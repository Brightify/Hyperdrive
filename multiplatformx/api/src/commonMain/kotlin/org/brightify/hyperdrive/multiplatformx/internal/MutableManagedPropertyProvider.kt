package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.MutableViewModelProperty

internal class MutableManagedPropertyProvider<OWNER: BaseViewModel, VM: ManageableViewModel?>(
    private val publishedChanges: Boolean,
    private val managedPropertyFactory: (owner: OWNER) -> MutableViewModelProperty<VM>,
): MutableViewModelPropertyProvider<OWNER, VM>(
    viewModelPropertyFactory = { owner ->
        managedPropertyFactory(owner)
            .also { ManagedPropertyHandler(owner, it, publishedChanges) }
    }
)