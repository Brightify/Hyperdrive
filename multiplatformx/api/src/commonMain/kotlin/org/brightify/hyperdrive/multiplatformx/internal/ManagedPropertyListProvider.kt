package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.scan
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class ManagedPropertyListProvider<OWNER, VM: ManageableViewModel?>(
    private val owner: BaseViewModel,
    private val objectWillChangeTrigger: ManageableViewModel.ObjectWillChangeTrigger,
    private val initialChild: List<VM>,
    private val publishedChanges: Boolean,
): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, List<VM>>> {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, List<VM>> {
        val childList = owner.getPropertyObserver(property, initialChild)

        var latestChildListToLifecycleSet = childList.value.let { it to it.mapNotNull { it?.lifecycle }.toSet() }
        owner.lifecycle.addChildren(latestChildListToLifecycleSet.second)

        fun replaceChild(newChildList: List<VM>) {
            if (newChildList != latestChildListToLifecycleSet.first) {
                val newLifecycleSet = newChildList.mapNotNull { it?.lifecycle }.toSet()
                val childrenToRemove = latestChildListToLifecycleSet.second - newLifecycleSet
                val childrenToAdd = newLifecycleSet - latestChildListToLifecycleSet.second

                owner.lifecycle.removeChildren(childrenToRemove)
                latestChildListToLifecycleSet = newChildList to newLifecycleSet
                owner.lifecycle.addChildren(childrenToAdd)
            }
        }

        owner.lifecycle.whileAttached {
            // The `child` could've changed while the lifecycle was detached so we need to check and
            // replace the old one.
            replaceChild(childList.value)

            childList.collect(::replaceChild)
        }

        if (publishedChanges) {
            owner.lifecycle.whileAttached {
                childList
                    .scan(emptyList<CancellationToken>()) { accumulator, children ->
                        accumulator.forEach { it.cancel() }
                        children.mapNotNull { child ->
                            child?.willChange?.addListener(objectWillChangeTrigger)
                        }
                    }
                    .collect()
            }
        }

        return MutableStateFlowBackedProperty(objectWillChangeTrigger, childList)
    }
}

