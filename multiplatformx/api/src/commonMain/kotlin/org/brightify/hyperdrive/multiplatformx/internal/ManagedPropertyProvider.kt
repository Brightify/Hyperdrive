package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.zip
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.Lifecycle
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class ManagedPropertyProvider<OWNER, T: BaseViewModel?>(
    private val owner: BaseViewModel,
    private val initialChild: T
): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, T> {
        val child = owner.getPropertyObserver(property, initialChild)

        owner.lifecycle.whileAttached {
            val previousChild = child.map {
                // This cast is not useless, without it we can't emit null in the `onStart` operator.
                @Suppress("USELESS_CAST")
                it as T?
            }.onStart { emit(null) }

            previousChild.zip(child) { oldChild, newChild ->
                oldChild to newChild
            }.collect {
                val (oldChild, newChild) = it
                // Nothing to do if the child is the same.
                if (oldChild == newChild) { return@collect }
                if (oldChild != null) {
                    owner.lifecycle.removeChild(oldChild.lifecycle)
                }

                if (newChild != null) {
                    owner.lifecycle.addChild(newChild.lifecycle)
                }
            }
        }

        owner.lifecycle.whileAttached {
            child.flatMapLatest { it?.observeObjectWillChange ?: emptyFlow() }.collect {
                owner.internalNotifyObjectWillChange()
            }
        }

        return MutableStateFlowBackedProperty(owner, child)
    }
}