package org.brightify.hyperdrive.util.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public open class NonNullFlowWrapper<T: Any>(
    private val origin: Flow<T>
): Flow<T> by origin {
    public open fun watch(
        onNext: (T) -> Unit,
        onError: (Exception) -> Unit,
        onCompleted: () -> Unit,
        coroutineScope: CoroutineScope = MainScope()
    ): FlowWrapperWatchToken = FlowWrapperWatchToken(this, coroutineScope, onNext, onError, onCompleted)

    public companion object {
        public fun <T: Any> wrap(flow: Flow<T>): NonNullFlowWrapper<T> {
            return NonNullFlowWrapper(origin = flow)
        }

        public fun <T: Any> wrapNonNullList(flow: Flow<List<T>>): NonNullFlowWrapper<NonNullListWrapper<T>> {
            return NonNullFlowWrapper(flow.map(::NonNullListWrapper))
        }

        public fun <T: Any> wrapNullableList(flow: Flow<List<T?>>): NonNullFlowWrapper<NullableListWrapper<T>> {
            return NonNullFlowWrapper(flow.map(::NullableListWrapper))
        }
    }
}
