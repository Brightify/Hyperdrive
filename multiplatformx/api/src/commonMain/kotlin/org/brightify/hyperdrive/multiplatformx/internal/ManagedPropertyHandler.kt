package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty

internal class ManagedPropertyHandler<VM: ManageableViewModel?>(
    private val owner: BaseViewModel,
    private val property: ViewModelProperty<VM>,
    private val publishedChanges: Boolean,
) {
    private var publishJobCancellation: CancellationToken? = null

    init {
        addChild(property.value)
        property.addListener(object: ViewModelProperty.ValueChangeListener<VM> {
            override fun valueDidChange(oldValue: VM) {
                removeChild(oldValue)
                addChild(property.value)
            }
        })
    }

    private fun addChild(child: VM) {
        child?.lifecycle?.let(owner.lifecycle::addChild)
        publishJobCancellation = if (publishedChanges) {
            child?.changeTracking?.addListener(owner.changeTrackingTrigger)
        } else {
            null
        }
    }

    private fun removeChild(oldChild: VM) {
        publishJobCancellation?.cancel()
        oldChild?.lifecycle?.let(owner.lifecycle::removeChild)
    }
}
