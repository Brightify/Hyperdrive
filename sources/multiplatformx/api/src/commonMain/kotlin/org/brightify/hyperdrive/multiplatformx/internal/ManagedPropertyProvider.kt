package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseObservableManageableObject
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

internal class ManagedPropertyProvider<OWNER: BaseObservableManageableObject, VM: ManageableViewModel?>(
    private val publishedChanges: Boolean,
    private val managedPropertyFactory: (owner: OWNER) -> ObservableProperty<VM>,
): ObservablePropertyProvider<OWNER, VM>(
    observablePropertyFactory = { owner ->
        ManagedPropertyHandler(owner, managedPropertyFactory(owner), publishedChanges)
    }
)
