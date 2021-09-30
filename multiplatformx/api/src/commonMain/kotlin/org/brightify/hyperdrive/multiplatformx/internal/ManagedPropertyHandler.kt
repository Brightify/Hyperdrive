package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseObservableManageableObject
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

internal class ManagedPropertyHandler<VM: ManageableViewModel?>(
    private val owner: BaseObservableManageableObject,
    private val property: ObservableProperty<VM>,
    private val publishedChanges: Boolean,
): ObservableProperty.Listener<VM> {
    private var publishJobCancellation: CancellationToken? = null

    init {
        addChild(property.value)
        property.addListener(this)
    }

    override fun valueDidChange(oldValue: VM, newValue: VM) {
        removeChild(oldValue)
        addChild(property.value)
    }

    private fun addChild(child: VM) {
        child?.lifecycle?.let(owner.lifecycle::addChild)
        publishJobCancellation = if (publishedChanges) {
            child?.changeTracking?.addListener(owner.internalChangeTrackingTrigger)
        } else {
            null
        }
    }

    private fun removeChild(oldChild: VM) {
        publishJobCancellation?.cancel()
        oldChild?.lifecycle?.let(owner.lifecycle::removeChild)
    }
}
