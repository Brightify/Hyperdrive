package org.brightify.hyperdrive.internal

import org.brightify.hyperdrive.BaseObservableManageableObject
import org.brightify.hyperdrive.ManageableViewModel
import org.brightify.hyperdrive.property.MutableObservableProperty

internal class MutableManagedListPropertyHandler<VM: ManageableViewModel?>(
    owner: BaseObservableManageableObject,
    private val property: MutableObservableProperty<List<VM>>,
    publishedChanges: Boolean,
): ManagedListPropertyHandler<VM>(
    owner, property, publishedChanges
), MutableObservableProperty<List<VM>> {
    override var value: List<VM>
        get() = property.value
        set(value) { property.value = value }
}
