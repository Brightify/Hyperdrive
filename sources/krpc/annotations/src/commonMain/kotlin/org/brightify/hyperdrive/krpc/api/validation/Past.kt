package org.brightify.hyperdrive.krpc.api.validation

/**
 * The element must be a date (possibly with time) in the past.
 * Supported types:
 * <ul>
 *     <li>Date</li>
 *     <li>Instant</li>
 *     <li>LocalDate</li>
 *     <li>LocalDateTime</li>
 *     <li>Calendar</li>
 *     <li>Long (ms from epoch)</li>
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
public annotation class Past(
    val message: String = "",
)
