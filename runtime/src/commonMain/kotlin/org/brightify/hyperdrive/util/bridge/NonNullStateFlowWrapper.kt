package org.brightify.hyperdrive.util.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

public open class NonNullStateFlowWrapper<T: Any>(
    private val getter: () -> T,
    private val origin: Flow<T>
): NonNullFlowWrapper<T>(origin) {
    public open val value: T
        get() = getter()

    public companion object {
        public fun <T: Any> wrap(flow: StateFlow<T>): NonNullStateFlowWrapper<T> {
            return NonNullStateFlowWrapper(getter = { flow.value }, origin = flow)
        }

        public fun <T: Any> wrapNonNullList(flow: StateFlow<List<T>>): NonNullStateFlowWrapper<NonNullListWrapper<T>> {
            return NonNullStateFlowWrapper(getter = { NonNullListWrapper(flow.value) }, flow.map(::NonNullListWrapper))
        }

        public fun <T: Any> wrapNullableList(flow: StateFlow<List<T?>>): NonNullStateFlowWrapper<NullableListWrapper<T>> {
            return NonNullStateFlowWrapper(getter = { NullableListWrapper(flow.value) }, flow.map(::NullableListWrapper))
        }
    }
}
