package org.brightify.hyperdrive.internal

import org.brightify.hyperdrive.BaseObservableManageableObject
import org.brightify.hyperdrive.ManageableViewModel
import org.brightify.hyperdrive.property.MutableObservableProperty

internal class MutableManagedPropertyProvider<OWNER: BaseObservableManageableObject, VM: ManageableViewModel?>(
    private val publishedChanges: Boolean,
    private val managedPropertyFactory: (owner: OWNER) -> MutableObservableProperty<VM>,
): MutableObservablePropertyProvider<OWNER, VM>(
    observablePropertyFactory = { owner ->
        MutableManagedPropertyHandler(owner, managedPropertyFactory(owner), publishedChanges)
    }
)
