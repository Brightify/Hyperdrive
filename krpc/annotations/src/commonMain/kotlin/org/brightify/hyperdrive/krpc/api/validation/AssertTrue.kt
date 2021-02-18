package org.brightify.hyperdrive.krpc.api.validation

/**
 * The element must be true.
 * Supported types:
 * <ul>
 *     <li>Boolean</li>
 * </ul>
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
annotation class AssertTrue(
    val message: String = "",
)