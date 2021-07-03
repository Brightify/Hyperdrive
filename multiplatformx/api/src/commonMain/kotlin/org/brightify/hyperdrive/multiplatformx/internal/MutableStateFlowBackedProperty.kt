package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.flow.MutableStateFlow
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal open class MutableStateFlowBackedProperty<OWNER, T>(
    private val objectWillChangeTrigger: ManageableViewModel.ObjectWillChangeTrigger,
    private val stateFlow: MutableStateFlow<T>
): ReadWriteProperty<OWNER, T> {

    override fun getValue(thisRef: OWNER, property: KProperty<*>): T {
        return stateFlow.value
    }

    override fun setValue(thisRef: OWNER, property: KProperty<*>, value: T) {
        objectWillChangeTrigger.notifyObjectWillChange()
        stateFlow.value = value
    }
}