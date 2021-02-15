package org.brightify.hyperdrive.multiplatformx

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@Deprecated("Use view models with the @ViewModel annotation.")
public annotation class EnhanceMultiplatform {
    public enum class Feature {
        FlowWrapper,
        FlowCallback,
    }
}
