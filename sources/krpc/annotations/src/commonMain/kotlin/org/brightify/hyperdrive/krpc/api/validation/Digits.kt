package org.brightify.hyperdrive.krpc.api.validation

/**
 * The element must contain the specified number of integer and fraction digits.
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
public annotation class Digits(
    val integer: Int,
    val fraction: Int,
    val message: String = "",
) {

    /**
     * Defines multiple <code>Digits</code> annotations on a single element.
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
    public annotation class List(
        val value: Array<Digits>,
    )
}
