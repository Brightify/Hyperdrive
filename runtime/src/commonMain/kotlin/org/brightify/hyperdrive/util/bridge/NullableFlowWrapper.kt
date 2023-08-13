package org.brightify.hyperdrive.util.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public open class NullableFlowWrapper<T: Any>(
    private val origin: Flow<T?>
): Flow<T?> by origin {
    public open fun watch(
        onNext: (T?) -> Unit,
        onError: (Exception) -> Unit,
        onCompleted: () -> Unit,
        coroutineScope: CoroutineScope = MainScope(),
    ): FlowWrapperWatchToken = FlowWrapperWatchToken(this, coroutineScope, onNext, onError, onCompleted)

    public companion object {
        public fun <T: Any> wrap(flow: Flow<T?>): NullableFlowWrapper<T> {
            return NullableFlowWrapper(origin = flow)
        }

        public fun <T: Any> wrapNonNullList(flow: Flow<List<T>?>): NullableFlowWrapper<NonNullListWrapper<T>> {
            return NullableFlowWrapper(flow.map { it?.let(::NonNullListWrapper) })
        }

        public fun <T: Any> wrapNullableList(flow: Flow<List<T?>?>): NullableFlowWrapper<NullableListWrapper<T>> {
            return NullableFlowWrapper(flow.map { it?.let(::NullableListWrapper) })
        }
    }
}
