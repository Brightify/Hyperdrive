package org.brightify.hyperdrive.multiplatformx.property

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.impl.DeferredFilterViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.DeferredToImmediateViewModelPropertyWrapper
import org.brightify.hyperdrive.multiplatformx.property.impl.FilterViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.ImmediateToDeferredViewModelPropertyWrapper
import org.brightify.hyperdrive.multiplatformx.property.impl.MapViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.FlatMapLatestViewModelProperty
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

public interface ViewModelProperty<T> {
    public val value: T

    public fun addListener(listener: ValueChangeListener<T>): CancellationToken

    public fun removeListener(listener: ValueChangeListener<T>): Boolean

    public interface ValueChangeListener<T> {
        public fun valueWillChange(newValue: T) { }

        public fun valueDidChange(oldValue: T) { }
    }

    public fun interface EqualityPolicy<T> {
        public fun isEqual(oldValue: T, newValue: T): Boolean
    }
}

public fun <T> defaultEqualityPolicy(): ViewModelProperty.EqualityPolicy<T> = ViewModelProperty.EqualityPolicy { oldValue, newValue ->
    oldValue == newValue
}

public fun <T> neverEqualPolicy(): ViewModelProperty.EqualityPolicy<T> = ViewModelProperty.EqualityPolicy { _, _ ->
    false
}

public fun <T> identityEqualityPolicy(): ViewModelProperty.EqualityPolicy<T> = ViewModelProperty.EqualityPolicy { oldValue, newValue ->
    oldValue === newValue
}

public fun <T, U> ViewModelProperty<T>.map(
    equalityPolicy: ViewModelProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
    transform: (T) -> U
): ViewModelProperty<U> {
    return MapViewModelProperty(this, transform, equalityPolicy)
}
public fun <T, U> ViewModelProperty<T>.flatMapLatest(
    equalityPolicy: ViewModelProperty.EqualityPolicy<ViewModelProperty<U>> = identityEqualityPolicy(),
    transform: (T) -> ViewModelProperty<U>,
): ViewModelProperty<U> {
    return FlatMapLatestViewModelProperty(this, transform, equalityPolicy)
}

public fun <T> ViewModelProperty<T>.filter(
    equalityPolicy: ViewModelProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    predicate: (T) -> Boolean,
): DeferredViewModelProperty<T> {
    return DeferredFilterViewModelProperty(this, predicate, equalityPolicy)
}

public fun <T> ViewModelProperty<T>.filter(
    initialValue: T,
    equalityPolicy: ViewModelProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    predicate: (T) -> Boolean,
): ViewModelProperty<T> {
    return FilterViewModelProperty(this, initialValue, predicate, equalityPolicy)
}

public suspend fun <T> ViewModelProperty<T>.nextValue(): T {
    val completable = CompletableDeferred<T>()
    val listener = object: ViewModelProperty.ValueChangeListener<T> {
        override fun valueDidChange(oldValue: T) {
            completable.complete(oldValue)
            removeListener(this)
        }
    }
    addListener(listener)
    completable.invokeOnCompletion {
        removeListener(listener)
    }
    return completable.await()
}

internal fun <OWNER, T> ViewModelProperty<T>.toKotlinProperty(): ReadOnlyProperty<OWNER, T> = ReadOnlyProperty { _, _ ->
    this@toKotlinProperty.value
}

internal fun <OWNER, T> MutableViewModelProperty<T>.toKotlinMutableProperty(): ReadWriteProperty<OWNER, T> =
    object: ReadWriteProperty<OWNER, T> {
        override fun getValue(thisRef: OWNER, property: KProperty<*>): T = this@toKotlinMutableProperty.value

        override fun setValue(thisRef: OWNER, property: KProperty<*>, value: T) {
            this@toKotlinMutableProperty.value = value
        }
    }

public fun <T> ViewModelProperty<T>.asChannel(): Channel<T> {
    val channel = Channel<T>(Channel.CONFLATED)
    val listener = object: ViewModelProperty.ValueChangeListener<T> {
        override fun valueDidChange(oldValue: T) {
            channel.trySend(value)
        }
    }
    addListener(listener)
    channel.invokeOnClose {
        removeListener(listener)
    }
    return channel
}

public fun <T> ViewModelProperty<T>.asFlow(): Flow<T> {
    return asChannel().consumeAsFlow()
}

public fun <T> ViewModelProperty<T>.deferred(): DeferredViewModelProperty<T> {
    return ImmediateToDeferredViewModelPropertyWrapper(this)
}

public fun <T> DeferredViewModelProperty<T>.startWith(initialValue: T): ViewModelProperty<T> {
    return DeferredToImmediateViewModelPropertyWrapper(initialValue, this)
}
