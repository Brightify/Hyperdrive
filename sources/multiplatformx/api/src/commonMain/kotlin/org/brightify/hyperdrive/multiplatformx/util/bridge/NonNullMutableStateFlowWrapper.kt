package org.brightify.hyperdrive.multiplatformx.util.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

public open class NonNullMutableStateFlowWrapper<T: Any>(
    private val getter: () -> T,
    private val setter: (T) -> Unit,
    private val origin: Flow<T>
): NonNullStateFlowWrapper<T>(getter, origin) {
    override var value: T
        get() = getter()
        set(newValue) {
            setter(newValue)
        }

    public companion object {
        public fun <T: Any> wrap(flow: MutableStateFlow<T>): NonNullMutableStateFlowWrapper<T> {
            return NonNullMutableStateFlowWrapper(getter = { flow.value }, setter = { flow.value = it }, origin = flow)
        }

        public fun <T: Any> wrapNonNullList(flow: MutableStateFlow<List<T>>): NonNullMutableStateFlowWrapper<NonNullListWrapper<T>> {
            return NonNullMutableStateFlowWrapper(getter = { NonNullListWrapper(flow.value) },
                { flow.value = it.origin },
                flow.map(::NonNullListWrapper))
        }

        public fun <T: Any> wrapNullableList(flow: MutableStateFlow<List<T?>>): NonNullMutableStateFlowWrapper<NullableListWrapper<T>> {
            return NonNullMutableStateFlowWrapper(getter = { NullableListWrapper(flow.value) },
                { flow.value = it.origin },
                flow.map(::NullableListWrapper))
        }
    }
}