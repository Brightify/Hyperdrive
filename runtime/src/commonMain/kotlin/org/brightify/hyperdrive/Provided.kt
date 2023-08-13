package org.brightify.hyperdrive

/**
 * Marks a constructor parameter as *provided*.
 *
 * Use to inform [AutoFactory] generator which parameters of a constructor are supposed to be provided when creating a new class (as opposed to
 * being provided to the Factory constructor).
 *
 * Future plans:
 *  - Support for renaming the parameter.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
public annotation class Provided
