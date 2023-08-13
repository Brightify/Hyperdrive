package org.brightify.hyperdrive.internal

import org.brightify.hyperdrive.BaseObservableManageableObject
import org.brightify.hyperdrive.ManageableViewModel
import org.brightify.hyperdrive.property.ObservableProperty

internal class ManagedListPropertyProvider<OWNER: BaseObservableManageableObject, VM: ManageableViewModel?>(
    private val publishedChanges: Boolean,
    private val managedPropertyFactory: (owner: OWNER) -> ObservableProperty<List<VM>>,
): ObservablePropertyProvider<OWNER, List<VM>>(
    observablePropertyFactory = { owner ->
        ManagedListPropertyHandler(owner, managedPropertyFactory(owner), publishedChanges)
    }
)
