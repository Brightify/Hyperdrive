package org.brightify.hyperdrive.multiplatformx

/**
 * Enables generating enhancements for the annotated class.
 *
 * **IMPORTANT**: Annotated class has to inherit from [BaseViewModel].
 *
 * ## Future plans
 * - Support extending which property delegates get the `observeX` properties generated.
 * - Drop the requirement of inheriting [BaseViewModel] and replace it with synthetic inheritance (blocked because of missing support in Kotlin).
 * - Support generics.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class ViewModel(
    // We can't use the `[...]` shorthand because it's not supported in JavaScript sourceSet.
    val observableDelegates: Array<String> = arrayOf(
        "published",
        "collected",
        "collectedFlatMap",
        "collectedFlatMapLatest",
        "binding",
        "managed",
        "managedList",
    ),
)









public annotation class Managed
public annotation class Model {
    public annotation class Id
}
public annotation class ComputedObservable
public annotation class Published
