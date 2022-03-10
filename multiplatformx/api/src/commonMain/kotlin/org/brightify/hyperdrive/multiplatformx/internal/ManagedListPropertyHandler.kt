package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseObservableManageableObject
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.Lifecycle
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.concat
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

internal open class ManagedListPropertyHandler<VM: ManageableViewModel?>(
    private val owner: BaseObservableManageableObject,
    private val property: ObservableProperty<List<VM>>,
    private val publishedChanges: Boolean,
): ObservableProperty.Listener<List<VM>>, ObservableProperty<List<VM>> {
    private var publishJobCancellation: CancellationToken? = null
    private var addedChildren: Set<Lifecycle> = emptySet()

    init {
        addChildren(property.value)
        property.addListener(this)
    }

    override fun valueDidChange(oldValue: List<VM>, newValue: List<VM>) {
        removeChildren()
        addChildren(property.value)
    }

    private fun addChildren(children: List<VM>) {
        val newLifecycles = children.mapNotNull { it?.lifecycle }.toSet()
        owner.lifecycle.addChildren(newLifecycles)
        addedChildren = newLifecycles

        publishJobCancellation = if (publishedChanges) {
            children.mapNotNull { it?.changeTracking?.addListener(owner.internalChangeTrackingTrigger) }.concat()
        } else {
            null
        }
    }

    private fun removeChildren() {
        publishJobCancellation?.cancel()
        owner.lifecycle.removeChildren(addedChildren)
    }

    override val value: List<VM>
        get() = property.value

    override fun addListener(listener: ObservableProperty.Listener<List<VM>>): CancellationToken = property.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<List<VM>>) = property.removeListener(listener)
}