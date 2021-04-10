package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.zip
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class ManagedPropertyListProvider<OWNER, VM: BaseViewModel?>(
    private val owner: BaseViewModel,
    private val initialChild: List<VM>,
    private val publishedChanges: Boolean,
): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, List<VM>>> {
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
            childList.collect(::replaceChild)
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

