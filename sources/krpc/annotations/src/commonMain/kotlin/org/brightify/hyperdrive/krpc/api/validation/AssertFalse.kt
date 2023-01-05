package org.brightify.hyperdrive.krpc.api.validation

/**
 * The element must be false.
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
public annotation class AssertFalse(
    val message: String = "",
)
