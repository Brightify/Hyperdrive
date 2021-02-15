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
    // val observableDelegates = listOf("collected", "published")
)