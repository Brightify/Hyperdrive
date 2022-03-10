package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseObservableManageableObject
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.MutableObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

internal class MutableManagedPropertyHandler<VM: ManageableViewModel?>(
    owner: BaseObservableManageableObject,
    private val property: MutableObservableProperty<VM>,
    publishedChanges: Boolean,
): ManagedPropertyHandler<VM>(owner, property, publishedChanges), ObservableProperty.Listener<VM>, MutableObservableProperty<VM> {
    override var value: VM
        get() = property.value
        set(value) { property.value = value }
}