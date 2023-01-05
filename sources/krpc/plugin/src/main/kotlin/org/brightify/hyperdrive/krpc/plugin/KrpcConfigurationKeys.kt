package org.brightify.hyperdrive.krpc.plugin

import org.jetbrains.kotlin.config.CompilerConfigurationKey

object KrpcConfigurationKeys {
    val isEnabled = CompilerConfigurationKey<Boolean>("enabled")
    val printIR = CompilerConfigurationKey<Boolean>("printIR")
    val printKotlinLike = CompilerConfigurationKey<Boolean>("printKotlinLike")
}