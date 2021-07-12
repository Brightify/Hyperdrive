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

internal class ManagedPropertyFlowProvider<OWNER, VM: ManageableViewModel?>(
    private val owner: BaseViewModel,
    private val objectWillChangeTrigger: ManageableViewModel.ObjectWillChangeTrigger,
    private val initialChild: VM,
    private val viewModelFlow: Flow<VM>,
    private val publishedChanges: Boolean,
): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, VM>> {
    @OptIn(ExperimentalCoroutinesApi::class)
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
                    objectWillChangeTrigger.notifyObjectWillChange()
                    child.value = newChild
                }
            }
        }

        if (publishedChanges) {
            owner.lifecycle.whileAttached {
                child
                    .scan(null as CancellationToken?) { accumulator, value ->
                        accumulator?.cancel()
                        value?.willChange?.addListener(objectWillChangeTrigger)
                    }
                    .collect()
            }
        }

        return MutableStateFlowBackedProperty(objectWillChangeTrigger, child)
    }
}