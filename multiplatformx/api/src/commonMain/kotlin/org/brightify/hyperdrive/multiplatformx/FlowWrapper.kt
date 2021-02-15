@file:Suppress("unused")

package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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
            return NonNullMutableStateFlowWrapper(getter = { NonNullListWrapper(flow.value) }, { flow.value = it.origin }, flow.map(::NonNullListWrapper))
        }

        public fun <T: Any> wrapNullableList(flow: MutableStateFlow<List<T?>>): NonNullMutableStateFlowWrapper<NullableListWrapper<T>> {
            return NonNullMutableStateFlowWrapper(getter = { NullableListWrapper(flow.value) }, { flow.value = it.origin }, flow.map(::NullableListWrapper))
        }
    }
}

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

public open class NonNullFlowWrapper<T: Any>(
    private val origin: Flow<T>
): Flow<T> by origin {
    public open fun watch(onNext: (T) -> Unit, onError: (Exception) -> Unit, onCompleted: () -> Unit): FlowWrapperWatchToken =
        FlowWrapperWatchToken(this, onNext, onError, onCompleted)

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
            return NullableMutableStateFlowWrapper(getter = { flow.value?.let(::NonNullListWrapper) }, { flow.value = it?.origin }, flow.map { it?.let(::NonNullListWrapper) })
        }

        public fun <T: Any> wrapNullableList(flow: MutableStateFlow<List<T?>?>): NullableMutableStateFlowWrapper<NullableListWrapper<T>> {
            return NullableMutableStateFlowWrapper(getter = { flow.value?.let(::NullableListWrapper) }, { flow.value = it?.origin }, flow.map { it?.let(::NullableListWrapper) })
        }
    }
}

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
            return NullableStateFlowWrapper(getter = { flow.value?.let(::NullableListWrapper) }, flow.map { it?.let(::NullableListWrapper) })
        }
    }
}

public open class NullableFlowWrapper<T: Any>(
    private val origin: Flow<T?>
): Flow<T?> by origin {
    public open fun watch(onNext: (T?) -> Unit, onError: (Exception) -> Unit, onCompleted: () -> Unit): FlowWrapperWatchToken =
        FlowWrapperWatchToken(this, onNext, onError, onCompleted)

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

public class FlowWrapperWatchToken private constructor() {

    public companion object {
        public operator fun <T> invoke(
            flow: Flow<T>,
            onNext: (T) -> Unit,
            onError: (Exception) -> Unit,
            onCompleted: () -> Unit
        ): FlowWrapperWatchToken {
            val token = FlowWrapperWatchToken()

            token.job = CoroutineScope(dispatcher()).launch {
                try {
                    token.awaitNextRequested()

                    flow.collect {
                        onNext(it)

                        token.awaitNextRequested()
                    }

                    onCompleted()
                } catch (e: CancellationException) {
                    onCompleted()
                } catch (e: Exception) {
                    e.printStackTrace()
                    onError(e)
                }
            }

            return token
        }
    }

    internal lateinit var job: Job

    private var deferred = CompletableDeferred<Unit>()

    internal suspend fun awaitNextRequested() {
        deferred.await()

        deferred = CompletableDeferred()
    }

    public fun requestNext() {
        deferred.complete(Unit)
    }

    public fun cancel() {
        job.cancel()
    }
}

public open class NonNullListWrapper<T: Any>(
    public val origin: List<T>
)

public open class NullableListWrapper<T: Any>(
    public val origin: List<T?>
)
