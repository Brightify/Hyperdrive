package org.brightify.hyperdrive.internal

import org.brightify.hyperdrive.BaseObservableManageableObject
import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.ManageableViewModel
import org.brightify.hyperdrive.property.ObservableProperty

internal open class ManagedPropertyHandler<VM: ManageableViewModel?>(
    private val owner: BaseObservableManageableObject,
    private val property: ObservableProperty<VM>,
    private val publishedChanges: Boolean,
): ObservableProperty.Listener<VM>, ObservableProperty<VM> {
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

    override val value: VM
        get() = property.value

    override fun addListener(listener: ObservableProperty.Listener<VM>): CancellationToken = property.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<VM>) = property.removeListener(listener)
}
