package org.brightify.hyperdrive.krpc.api.validation

/**
 * The element must match the regular expression.
 * Supported types:
 * <ul>
 *     <li>String</li>
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
annotation class Pattern(
    val regexp: String,
    val message: String = "",
    val flags: Array<Flag> = emptyArray(),
) {

    /**
     * Defines multiple <code>Pattern</code> annotations on a single element.
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
        val value: Array<Pattern>,
    )

    /**
     * Possible Regexp flags
     */
    enum class Flag {

        /**
         * Enables Unix lines mode
         */
        UNIX_LINES,

        /**
         * Enables case-insensitive matching
         */
        CASE_INSENSITIVE,

        /**
         * Permits whitespaces and comments in pattern
         */
        COMMENTS,

        /**
         * Enables multiline mode
         */
        MULTILINE,

        /**
         * Enables dotall mode
         */
        DOTALL,

        /**
         * Enables Unicode-aware case folding
         */
        UNICODE_CASE,

        /**
         * Enables canonical equivalence
         */
        CANONICAL_EQUIVALENCE;
    }
}
