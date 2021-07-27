package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.Lifecycle
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.concat

internal class ManagedListPropertyHandler<VM: ManageableViewModel?>(
    private val owner: BaseViewModel,
    private val property: ViewModelProperty<List<VM>>,
    private val publishedChanges: Boolean,
) {
    private var publishJobCancellation: CancellationToken? = null
    private var addedChildren: Set<Lifecycle> = emptySet()

    init {
        addChildren(property.value)
        property.addListener(object: ViewModelProperty.ValueChangeListener<List<VM>> {
            override fun valueDidChange(oldValue: List<VM>) {
                removeChildren()
                addChildren(property.value)
            }
        })
    }

    private fun addChildren(children: List<VM>) {
        val newLifecycles = children.mapNotNull { it?.lifecycle }.toSet()
        owner.lifecycle.addChildren(newLifecycles)
        addedChildren = newLifecycles

        publishJobCancellation = if (publishedChanges) {
            children.mapNotNull { it?.changeTracking?.addListener(owner.changeTrackingTrigger) }.concat()
        } else {
            null
        }
    }

    private fun removeChildren() {
        publishJobCancellation?.cancel()
        owner.lifecycle.removeChildren(addedChildren)
    }
}