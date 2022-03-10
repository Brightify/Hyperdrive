package org.brightify.hyperdrive.multiplatformx.compose

/**
 * Can be used to disable IR modifications on `@Composable` functions that provide automatic
 * `ManageableViewModel` change observation.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class NoAutoObserve