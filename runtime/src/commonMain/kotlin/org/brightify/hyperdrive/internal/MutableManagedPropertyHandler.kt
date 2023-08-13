package org.brightify.hyperdrive.internal

import org.brightify.hyperdrive.BaseObservableManageableObject
import org.brightify.hyperdrive.ManageableViewModel
import org.brightify.hyperdrive.property.MutableObservableProperty
import org.brightify.hyperdrive.property.ObservableProperty

internal class MutableManagedPropertyHandler<VM: ManageableViewModel?>(
    owner: BaseObservableManageableObject,
    private val property: MutableObservableProperty<VM>,
    publishedChanges: Boolean,
): ManagedPropertyHandler<VM>(owner, property, publishedChanges), ObservableProperty.Listener<VM>, MutableObservableProperty<VM> {
    override var value: VM
        get() = property.value
        set(value) { property.value = value }
}
