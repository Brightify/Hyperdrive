@file:Suppress("unused")

package org.brightify.hyperdrive.multiplatformx.property

import org.brightify.hyperdrive.multiplatformx.property.impl.CombineLatestObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.ConstantObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.DeferredFilterObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.DeferredToImmediateObservablePropertyWrapper
import org.brightify.hyperdrive.multiplatformx.property.impl.FilterObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.FlatMapLatestDeferredObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.FlatMapLatestObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.ImmediateToDeferredObservablePropertyWrapper
import org.brightify.hyperdrive.multiplatformx.property.impl.MapDeferredObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.MapObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.NeverDeferredObservableProperty
import kotlin.js.JsName

public fun <T> ObservableProperty.Companion.constant(value: T): ObservableProperty<T> {
    return ConstantObservableProperty(value)
}

public fun <T> ObservableProperty.Companion.never(): DeferredObservableProperty<T> {
    return NeverDeferredObservableProperty()
}

/**
 * A mapping function applied to each element collected from the [ObservableProperty].
 */
public fun <T, U> ObservableProperty<T>.map(
    equalityPolicy: ObservableProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
    transform: (T) -> U,
): ObservableProperty<U> {
    return MapObservableProperty(this, transform, equalityPolicy)
}

/**
 * A mapping function applied to each element collected from the [DeferredObservableProperty].
 */
public fun <T, U> DeferredObservableProperty<T>.map(
    equalityPolicy: ObservableProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
    transform: (T) -> U,
): DeferredObservableProperty<U> {
    return MapDeferredObservableProperty(this, transform, equalityPolicy)
}

/**
 * A mapping function applied to each element collected from the [ObservableProperty] to return another [ObservableProperty].
 */
public fun <T, U> ObservableProperty<T>.flatMapLatest(
    equalityPolicy: ObservableProperty.EqualityPolicy<ObservableProperty<U>> = identityEqualityPolicy(),
    transform: (T) -> ObservableProperty<U>,
): ObservableProperty<U> {
    return FlatMapLatestObservableProperty(this, transform, equalityPolicy)
}

/**
 * A mapping function applied to each element collected from the [ObsrvableProperty] to return a [DeferredObservableProperty].
 */
public fun <T, U> DeferredObservableProperty<T>.flatMapLatest(
    equalityPolicy: ObservableProperty.EqualityPolicy<DeferredObservableProperty<U>> = identityEqualityPolicy(),
    transform: (T) -> DeferredObservableProperty<U>,
): DeferredObservableProperty<U> {
    return FlatMapLatestDeferredObservableProperty(this, transform, equalityPolicy)
}

/**
 * A filter function applied to each element collected from the [ObservableProperty].
 */
public fun <T> ObservableProperty<T>.filter(
    equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    predicate: (T) -> Boolean,
): DeferredObservableProperty<T> {
    return DeferredFilterObservableProperty(this, predicate, equalityPolicy)
}

/**
 * A filter function applied to each element collected from the [ObservableProperty].
 */
public fun <T> ObservableProperty<T>.filter(
    initialValue: T,
    equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    predicate: (T) -> Boolean,
): ObservableProperty<T> {
    return FilterObservableProperty(this, initialValue, predicate, equalityPolicy)
}

/**
 * Keep each non-null element collected from the [ObservableProperty].
 */
public fun <T: Any> ObservableProperty<T?>.filterNotNull(
    equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
): DeferredObservableProperty<T> {
    return filter { it != null }.map(equalityPolicy) { it!! }
}

/**
 * Combine the last values of all provided [ObservableProperty] into a list of values.
 */
public fun <T> combine(sources: List<ObservableProperty<T>>): ObservableProperty<List<T>> {
    return CombineLatestObservableProperty(sources)
}

/**
 * Combine the last values of two [ObservableProperty] into a [Pair].
 */
public fun <T1, T2> combine(
    source1: ObservableProperty<T1>,
    source2: ObservableProperty<T2>,
): ObservableProperty<Pair<T1, T2>> = combine(source1, source2, ::Pair)

/**
 * Combine the last values of two [ObservableProperty] with a mapping function.
 */
@Suppress("UNCHECKED_CAST")
public fun <T1, T2, U> combine(
    source1: ObservableProperty<T1>,
    source2: ObservableProperty<T2>,
    combine: (T1, T2) -> U,
): ObservableProperty<U> {
    return CombineLatestObservableProperty(
        listOf(
            source1 as ObservableProperty<Any?>,
            source2 as ObservableProperty<Any?>,
        )
    )
    .map {
        val (t1, t2) = it
        combine(t1 as T1, t2 as T2)
    }
}

/**
 * Combine the last values of three [ObservableProperty] into a [Triple].
 */
public fun <T1, T2, T3, U> combine(
    source1: ObservableProperty<T1>,
    source2: ObservableProperty<T2>,
    source3: ObservableProperty<T3>,
): ObservableProperty<Triple<T1, T2, T3>> = combine(source1, source2, source3, ::Triple)

/**
 * Combine the last values of three [ObservableProperty] with a mapping function.
 */
@Suppress("UNCHECKED_CAST")
public fun <T1, T2, T3, U> combine(
    source1: ObservableProperty<T1>,
    source2: ObservableProperty<T2>,
    source3: ObservableProperty<T3>,
    combine: (T1, T2, T3) -> U,
): ObservableProperty<U> {
    return CombineLatestObservableProperty(
        listOf(
            source1 as ObservableProperty<Any?>,
            source2 as ObservableProperty<Any?>,
            source3 as ObservableProperty<Any?>,
        )
    ).map {
        val (t1, t2, t3) = it
        combine(t1 as T1, t2 as T2, t3 as T3)
    }
}

/**
 * Combine the last values of four [ObservableProperty] with a mapping function.
 */
@Suppress("UNCHECKED_CAST")
public fun <T1, T2, T3, T4, U> combine(
    source1: ObservableProperty<T1>,
    source2: ObservableProperty<T2>,
    source3: ObservableProperty<T3>,
    source4: ObservableProperty<T4>,
    combine: (T1, T2, T3, T4) -> U,
): ObservableProperty<U> {
    return CombineLatestObservableProperty(
        listOf(
            source1 as ObservableProperty<Any?>,
            source2 as ObservableProperty<Any?>,
            source3 as ObservableProperty<Any?>,
            source4 as ObservableProperty<Any?>,
        )
    ).map {
        val (t1, t2, t3, t4) = it
        combine(t1 as T1, t2 as T2, t3 as T3, t4 as T4)
    }
}

/**
 * Combine the last values of five [ObservableProperty] with a mapping function.
 */
@Suppress("UNCHECKED_CAST")
public fun <T1, T2, T3, T4, T5, U> combine(
    source1: ObservableProperty<T1>,
    source2: ObservableProperty<T2>,
    source3: ObservableProperty<T3>,
    source4: ObservableProperty<T4>,
    source5: ObservableProperty<T5>,
    combine: (T1, T2, T3, T4, T5) -> U,
): ObservableProperty<U> {
    return CombineLatestObservableProperty(
        listOf(
            source1 as ObservableProperty<Any?>,
            source2 as ObservableProperty<Any?>,
            source3 as ObservableProperty<Any?>,
            source4 as ObservableProperty<Any?>,
            source5 as ObservableProperty<Any?>,
        )
    ).map {
        val (t1, t2, t3, t4, t5) = it
        combine(t1 as T1, t2 as T2, t3 as T3, t4 as T4, t5 as T5)
    }
}

/**
 * Conversion method to asynchronous [DeferredObservableProperty].
 */
public fun <T> ObservableProperty<T>.deferred(): DeferredObservableProperty<T> {
    return ImmediateToDeferredObservablePropertyWrapper(this)
}

/**
 * Conversion method to synchronous [ObservableProperty].
 */
public fun <T> DeferredObservableProperty<T>.startWith(initialValue: T): ObservableProperty<T> {
    return DeferredToImmediateObservablePropertyWrapper(initialValue, this)
}
