package org.brightify.hyperdrive.debug

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.extensions.LoadingOrder
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension

@AutoService(ComponentRegistrar::class)
class DebugComponentRegistrar: ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        val isEnabled = configuration.getBoolean(DebugConfigurationKeys.isEnabled)
        if (!isEnabled) {
            return
        }

        val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)

        val extension = DebugIrGenerationExtension(
            disablePrintIR = configuration.getBoolean(DebugConfigurationKeys.disablePrintIR),
            disablePrintKotlinLike = configuration.getBoolean(DebugConfigurationKeys.disablePrintKotlinLike),
            messageCollector,
        )
        @Suppress("UnstableApiUsage")
        project.extensionArea.getExtensionPoint(IrGenerationExtension.extensionPointName)
            .registerExtension(extension, LoadingOrder.LAST, project)
    }
}