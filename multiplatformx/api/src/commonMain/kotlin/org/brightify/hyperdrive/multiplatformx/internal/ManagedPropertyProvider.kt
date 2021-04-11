package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.zip
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class ManagedPropertyProvider<OWNER, VM: ManageableViewModel?>(
    private val owner: BaseViewModel,
    private val initialChild: VM,
    private val publishedChanges: Boolean,
): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, VM>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, VM> {
        val child = owner.getPropertyObserver(property, initialChild)

        var latestChildLifecycle = child.value?.lifecycle
        latestChildLifecycle?.let(owner.lifecycle::addChild)

        fun replaceChild(newChild: VM) {
            val newLifecycle = newChild?.lifecycle
            if (newLifecycle != latestChildLifecycle) {
                latestChildLifecycle?.let(owner.lifecycle::removeChild)
                latestChildLifecycle = newLifecycle
                newChild?.lifecycle?.let(owner.lifecycle::addChild)
            }
        }

        owner.lifecycle.whileAttached {
            child.collect(::replaceChild)
        }

        if (publishedChanges) {
            owner.lifecycle.whileAttached {
                child.flatMapLatest { it?.observeObjectWillChange ?: emptyFlow() }.collect {
                    owner.internalNotifyObjectWillChange()
                }
            }
        }

        return MutableStateFlowBackedProperty(owner, child)
    }
}