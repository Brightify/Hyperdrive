package org.brightify.hyperdrive.internal

import org.brightify.hyperdrive.BaseObservableManageableObject
import org.brightify.hyperdrive.ManageableViewModel
import org.brightify.hyperdrive.property.MutableObservableProperty

internal class MutableManagedListPropertyProvider<OWNER: BaseObservableManageableObject, VM: ManageableViewModel?>(
    private val publishedChanges: Boolean,
    private val managedPropertyFactory: (owner: OWNER) -> MutableObservableProperty<List<VM>>,
): MutableObservablePropertyProvider<OWNER, List<VM>>(
    observablePropertyFactory = { owner ->
        MutableManagedListPropertyHandler(owner, managedPropertyFactory(owner), publishedChanges)
    },
)
