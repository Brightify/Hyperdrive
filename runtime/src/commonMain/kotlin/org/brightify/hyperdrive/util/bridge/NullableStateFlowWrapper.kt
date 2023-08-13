package org.brightify.hyperdrive.util.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

public open class NullableStateFlowWrapper<T: Any>(
    private val getter: () -> T?,
    private val origin: Flow<T?>
): NullableFlowWrapper<T>(origin) {
    public open val value: T?
        get() = getter()

    public companion object {
        public fun <T: Any> wrap(flow: StateFlow<T?>): NullableStateFlowWrapper<T> {
            return NullableStateFlowWrapper(getter = { flow.value }, origin = flow)
        }

        public fun <T: Any> wrapNonNullList(flow: StateFlow<List<T>?>): NullableStateFlowWrapper<NonNullListWrapper<T>> {
            return NullableStateFlowWrapper(getter = { flow.value?.let(::NonNullListWrapper) }, flow.map { it?.let(::NonNullListWrapper) })
        }

        public fun <T: Any> wrapNullableList(flow: StateFlow<List<T?>?>): NullableStateFlowWrapper<NullableListWrapper<T>> {
            return NullableStateFlowWrapper(getter = { flow.value?.let(::NullableListWrapper) },
                flow.map { it?.let(::NullableListWrapper) })
        }
    }
}
