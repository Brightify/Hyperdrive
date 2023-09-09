package org.brightify.hyperdrive.internal

import org.brightify.hyperdrive.BaseObservableManageableObject
import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.Lifecycle
import org.brightify.hyperdrive.ManageableViewModel
import org.brightify.hyperdrive.concat
import org.brightify.hyperdrive.property.ObservableProperty

internal open class ManagedListPropertyHandler<VM: ManageableViewModel?>(
    private val owner: BaseObservableManageableObject,
    private val property: ObservableProperty<List<VM>>,
    private val publishedChanges: Boolean,
): ObservableProperty.Listener<List<VM>>, ObservableProperty<List<VM>> {
    private var publishJobCancellation: CancellationToken? = null
    private var subscribedChildren = emptySet<Lifecycle>()

    init {
        updateLifecycles(property.value)
        property.addListener(this)
    }

    override fun valueDidChange(oldValue: List<VM>, newValue: List<VM>) {
        updateLifecycles(newValue)
    }

    private fun updateLifecycles(newChildren: List<VM>) {
        val newLifecycles = newChildren.mapNotNull{ it?.lifecycle }.toSet()
        val lifecyclesToRemove = subscribedChildren - newLifecycles
        val lifecyclesToAdd = newLifecycles - subscribedChildren

        owner.lifecycle.removeChildren(lifecyclesToRemove)
        owner.lifecycle.addChildren(lifecyclesToAdd)

        subscribedChildren = newLifecycles

        stopObservingChildren()
        startObservingChildrenChangesIfNeeded(newChildren)
    }

    private fun startObservingChildrenChangesIfNeeded(children: List<VM>) {
        publishJobCancellation = if (publishedChanges) {
            children.mapNotNull { it?.changeTracking?.addListener(owner.internalChangeTrackingTrigger) }.concat()
        } else {
            null
        }
    }

    private fun stopObservingChildren() {
        publishJobCancellation?.cancel()
    }

    override val value: List<VM>
        get() = property.value

    override fun addListener(listener: ObservableProperty.Listener<List<VM>>): CancellationToken = property.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<List<VM>>) = property.removeListener(listener)
}
