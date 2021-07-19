package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.scan
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal class ManagedPropertyFlowListProvider<OWNER, VM: ManageableViewModel?>(
    private val owner: BaseViewModel,
    private val objectWillChangeTrigger: ManageableViewModel.ObjectWillChangeTrigger,
    private val initialChild: List<VM>,
    private val viewModelFlow: Flow<List<VM>>,
    private val publishedChanges: Boolean,
): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, List<VM>>> {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadOnlyProperty<OWNER, List<VM>> {
        val childList = owner.getPropertyObserver(property, initialChild)

        var latestChildListToLifecycleSet = childList.value.let { it to it.mapNotNull { it?.lifecycle }.toSet() }
        owner.lifecycle.addChildren(latestChildListToLifecycleSet.second)

        fun replaceChild(newChildList: List<VM>, betweenReplacementDo: () -> Unit = { }) {
            if (newChildList != latestChildListToLifecycleSet.first) {
                val newLifecycleSet = newChildList.mapNotNull { it?.lifecycle }.toSet()
                val childrenToRemove = latestChildListToLifecycleSet.second - newLifecycleSet
                val childrenToAdd = newLifecycleSet - latestChildListToLifecycleSet.second

                owner.lifecycle.removeChildren(childrenToRemove)
                betweenReplacementDo()
                latestChildListToLifecycleSet = newChildList to newLifecycleSet
                owner.lifecycle.addChildren(childrenToAdd)
            }
        }

        owner.lifecycle.whileAttached {
            // The `child` could've changed while the lifecycle was detached so we need to check and
            // replace the old one.
            replaceChild(childList.value)

            viewModelFlow.collect { newChildList ->
                replaceChild(newChildList) {
                    objectWillChangeTrigger.notifyObjectWillChange()
                    childList.value = newChildList
                }
            }
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