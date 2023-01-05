package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseObservableManageableObject
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

internal class ManagedListPropertyProvider<OWNER: BaseObservableManageableObject, VM: ManageableViewModel?>(
    private val publishedChanges: Boolean,
    private val managedPropertyFactory: (owner: OWNER) -> ObservableProperty<List<VM>>,
): ObservablePropertyProvider<OWNER, List<VM>>(
    observablePropertyFactory = { owner ->
        ManagedListPropertyHandler(owner, managedPropertyFactory(owner), publishedChanges)
    }
)
