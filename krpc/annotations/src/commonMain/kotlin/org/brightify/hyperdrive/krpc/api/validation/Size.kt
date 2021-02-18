package org.brightify.hyperdrive.krpc.api.validation

/**
 * The element must be in between specified limits.
 * Supported types:
 * <ul>
 *     <li>String</li>
 *     <li>Array</li>
 *     <li>Collection</li>
 *     <li>Map</li>
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
annotation class Size(
    val min: Int = 0,
    val max: Int = Int.MAX_VALUE,
    val message: String = "",
) {

    /**
     * Defines multiple <code>Size</code> annotations on a single element.
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
    annotation class List(
        val value: Array<Size>,
    )
}
