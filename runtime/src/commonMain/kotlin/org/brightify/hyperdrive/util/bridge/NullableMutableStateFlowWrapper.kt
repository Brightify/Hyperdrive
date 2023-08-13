package org.brightify.hyperdrive.util.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

public open class NullableMutableStateFlowWrapper<T: Any>(
    private val getter: () -> T?,
    private val setter: (T?) -> Unit,
    private val origin: Flow<T?>
): NullableStateFlowWrapper<T>(getter, origin) {
    override var value: T?
        get() = getter()
        set(newValue) {
            setter(newValue)
        }

    public companion object {
        public fun <T: Any> wrap(flow: MutableStateFlow<T?>): NullableMutableStateFlowWrapper<T> {
            return NullableMutableStateFlowWrapper(getter = { flow.value }, setter = { flow.value = it }, origin = flow)
        }

        public fun <T: Any> wrapNonNullList(flow: MutableStateFlow<List<T>?>): NullableMutableStateFlowWrapper<NonNullListWrapper<T>> {
            return NullableMutableStateFlowWrapper(getter = { flow.value?.let(::NonNullListWrapper) },
                { flow.value = it?.origin },
                flow.map { it?.let(::NonNullListWrapper) })
        }

        public fun <T: Any> wrapNullableList(flow: MutableStateFlow<List<T?>?>): NullableMutableStateFlowWrapper<NullableListWrapper<T>> {
            return NullableMutableStateFlowWrapper(getter = { flow.value?.let(::NullableListWrapper) },
                { flow.value = it?.origin },
                flow.map { it?.let(::NullableListWrapper) })
        }
    }
}
