package org.brightify.hyperdrive.multiplatformx

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class EnhanceMultiplatform {
    enum class Feature {
        FlowWrapper,
        FlowCallback,
    }
}
