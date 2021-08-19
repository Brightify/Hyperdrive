package org.brightify.hyperdrive

open class HyperdriveExtension {
    var isMultiplatformEnabled
        get() = multiplatformx != null
        set(newValue) = when {
            newValue && multiplatformx == null -> multiplatformx = MultiplatformxSettings()
            !newValue && multiplatformx != null -> multiplatformx = null
            else -> { }
        }

    var isKrpcEnabled
        get() = krpc != null
        set(newValue) = when {
            newValue && krpc == null -> krpc = KrpcSettings()
            !newValue && krpc != null -> krpc = null
            else -> { }
        }

    var multiplatformx: MultiplatformxSettings? = null
        private set
    var krpc: KrpcSettings? = null
        private set

    fun multiplatformx() {
        multiplatformx = MultiplatformxSettings()
    }

    fun multiplatformx(configure: MultiplatformxSettings.() -> Unit) {
        val multiplatformx = MultiplatformxSettings()
        configure(multiplatformx)
        this.multiplatformx = multiplatformx
    }

    fun krpc() {
        krpc = KrpcSettings()
    }

    fun krpc(configure: KrpcSettings.() -> Unit) {
        val krpc = KrpcSettings()
        configure(krpc)
        this.krpc = krpc
    }

    class MultiplatformxSettings {
        var isAutoFactoryEnabled: Boolean = true
        var isViewModelEnabled: Boolean = true
        var isComposableAutoObserveEnabled: Boolean = true
    }

    class KrpcSettings {
        var printIR: Boolean = false
        var printKotlinLike: Boolean = false
    }
}