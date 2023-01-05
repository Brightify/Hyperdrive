package org.brightify.hyperdrive.krpc.api.validation

/**
 * The element must be a number larger than the specified value.
 * Supported types:
 * <ul>
 *     <li>BigDecimal</li>
 *     <li>BigInteger</li>
 *     <li>String</li>
 *     <li>Byte</li>
 *     <li>Short</li>
 *     <li>Int</li>
 *     <li>Long</li>
 *     <li>Float</li>
 *     <li>Double</li>
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
public annotation class Min(
    val value: Long,
    val message: String = "",
)
