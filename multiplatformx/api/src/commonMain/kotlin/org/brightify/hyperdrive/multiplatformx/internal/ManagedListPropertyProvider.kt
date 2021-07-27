package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty

internal class ManagedListPropertyProvider<OWNER: BaseViewModel, VM: ManageableViewModel?>(
    private val publishedChanges: Boolean,
    private val managedPropertyFactory: (owner: OWNER) -> ViewModelProperty<List<VM>>,
): ViewModelPropertyProvider<OWNER, List<VM>>(
    viewModelPropertyFactory = { owner ->
        managedPropertyFactory(owner)
            .also { ManagedListPropertyHandler(owner, it, publishedChanges) }
    }
)
