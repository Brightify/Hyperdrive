package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseObservableManageableObject
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.MutableObservableProperty

internal class MutableManagedListPropertyProvider<OWNER: BaseObservableManageableObject, VM: ManageableViewModel?>(
    private val publishedChanges: Boolean,
    private val managedPropertyFactory: (owner: OWNER) -> MutableObservableProperty<List<VM>>,
): MutableObservablePropertyProvider<OWNER, List<VM>>(
    observablePropertyFactory = { owner ->
        MutableManagedListPropertyHandler(owner, managedPropertyFactory(owner), publishedChanges)
    },
)
