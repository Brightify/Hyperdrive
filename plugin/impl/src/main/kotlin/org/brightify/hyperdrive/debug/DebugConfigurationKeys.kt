package org.brightify.hyperdrive.debug

import org.jetbrains.kotlin.config.CompilerConfigurationKey

object DebugConfigurationKeys {
    val isEnabled = CompilerConfigurationKey<Boolean>("enabled")
    val disablePrintIR = CompilerConfigurationKey<Boolean>("disablePrintIR")
    val disablePrintKotlinLike = CompilerConfigurationKey<Boolean>("disablePrintKotlinLike")
}