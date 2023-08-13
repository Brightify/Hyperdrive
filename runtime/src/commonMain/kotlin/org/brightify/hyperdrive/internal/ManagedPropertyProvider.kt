package org.brightify.hyperdrive.internal

import org.brightify.hyperdrive.BaseObservableManageableObject
import org.brightify.hyperdrive.ManageableViewModel
import org.brightify.hyperdrive.property.ObservableProperty

internal class ManagedPropertyProvider<OWNER: BaseObservableManageableObject, VM: ManageableViewModel?>(
    private val publishedChanges: Boolean,
    private val managedPropertyFactory: (owner: OWNER) -> ObservableProperty<VM>,
): ObservablePropertyProvider<OWNER, VM>(
    observablePropertyFactory = { owner ->
        ManagedPropertyHandler(owner, managedPropertyFactory(owner), publishedChanges)
    }
)
