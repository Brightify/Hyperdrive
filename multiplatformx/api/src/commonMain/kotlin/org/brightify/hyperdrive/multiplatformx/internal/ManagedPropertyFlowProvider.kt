package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEmpty
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal class ManagedPropertyFlowProvider<OWNER, VM: BaseViewModel?>(
    private val owner: BaseViewModel,
    private val initialChild: VM,
    private val viewModelFlow: Flow<VM>,
    private val publishedChanges: Boolean,
): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, VM>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadOnlyProperty<OWNER, VM> {
        val child = owner.getPropertyObserver(property, initialChild)

        var latestChildLifecycle = child.value?.lifecycle
        latestChildLifecycle?.let(owner.lifecycle::addChild)

        fun replaceChild(newChild: VM, betweenReplacementDo: () -> Unit = { }) {
            val newLifecycle = newChild?.lifecycle
            if (newLifecycle != latestChildLifecycle) {
                latestChildLifecycle?.let(owner.lifecycle::removeChild)
                betweenReplacementDo()
                latestChildLifecycle = newLifecycle
                newChild?.lifecycle?.let(owner.lifecycle::addChild)
            }
        }

        owner.lifecycle.whileAttached {
            replaceChild(child.value)
            viewModelFlow.collect { newChild ->
                replaceChild(newChild) {
                    owner.internalNotifyObjectWillChange()
                    child.value = newChild
                }
            }
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