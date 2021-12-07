package org.brightify.hyperdrive.krpc.api.validation

/**
 * The element must be <code>null</code> of any type.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FIELD,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.VALUE_PARAMETER
)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
public annotation class Null(
    val message: String = "",
)