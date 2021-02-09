package org.brightify.hyperdrive

open class HyperdriveExtension {
    val multiplatformx = MultiplatformxSettings()

    fun multiplatformx(configure: MultiplatformxSettings.() -> Unit) {
        configure(multiplatformx)
    }

    class MultiplatformxSettings {
        var isAutoFactoryEnabled: Boolean = true
    }
}