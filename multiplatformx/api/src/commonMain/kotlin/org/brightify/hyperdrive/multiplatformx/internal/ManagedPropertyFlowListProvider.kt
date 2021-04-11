package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal class ManagedPropertyFlowListProvider<OWNER, VM: ManageableViewModel?>(
    private val owner: BaseViewModel,
    private val initialChild: List<VM>,
    private val viewModelFlow: Flow<List<VM>>,
    private val publishedChanges: Boolean,
): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, List<VM>>> {
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
            replaceChild(childList.value)

            viewModelFlow.collect { newChildList ->
                replaceChild(newChildList) {
                    owner.internalNotifyObjectWillChange()
                    childList.value = newChildList
                }
            }
        }

        if (publishedChanges) {
            owner.lifecycle.whileAttached {
                childList
                    .flatMapLatest {
                        it.mapNotNull { child -> child?.observeObjectWillChange }.merge()
                    }
                    .collect {
                        owner.internalNotifyObjectWillChange()
                    }
            }
        }

        return MutableStateFlowBackedProperty(owner, childList)
    }
}