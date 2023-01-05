package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseObservableManageableObject
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.MutableObservableProperty

internal class MutableManagedPropertyProvider<OWNER: BaseObservableManageableObject, VM: ManageableViewModel?>(
    private val publishedChanges: Boolean,
    private val managedPropertyFactory: (owner: OWNER) -> MutableObservableProperty<VM>,
): MutableObservablePropertyProvider<OWNER, VM>(
    observablePropertyFactory = { owner ->
        MutableManagedPropertyHandler(owner, managedPropertyFactory(owner), publishedChanges)
    }
)