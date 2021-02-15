package org.brightify.hyperdrive.multiplatformx.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KProperty


internal operator fun <OWNER, T> StateFlow<T>.getValue(thisRef: OWNER, property: KProperty<*>): T = value

internal operator fun <OWNER, T> MutableStateFlow<T>.setValue(thisRef: OWNER, property: KProperty<*>, newValue: T) {
    value = newValue
}
