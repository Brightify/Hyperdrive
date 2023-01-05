package org.brightify.hyperdrive.krpc.plugin

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension

@AutoService(ComponentRegistrar::class)
class KrpcComponentRegistrar: ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        val isEnabled = configuration.getBoolean(KrpcConfigurationKeys.isEnabled)
        if (!isEnabled) {
            return
        }

        val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        messageCollector.report(CompilerMessageSeverity.WARNING, "Hyperdrive kRPC is experimental!")

        IrGenerationExtension.registerExtension(
            project,
            KrpcIrGenerationExtension(
                printIR = configuration.getBoolean(KrpcConfigurationKeys.printIR),
                printKotlinLike = configuration.getBoolean(KrpcConfigurationKeys.printKotlinLike)
            )
        )

        SyntheticResolveExtension.registerExtension(
            project,
            KrpcResolveExtension()
        )
    }
}