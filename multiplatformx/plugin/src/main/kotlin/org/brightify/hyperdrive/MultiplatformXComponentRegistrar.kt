package org.brightify.hyperdrive

import com.google.auto.service.AutoService
import org.brightify.hyperdrive.autofactory.AutoFactoryExpressionCodegenExtension
import org.brightify.hyperdrive.autofactory.AutoFactoryIrGenerationExtension
import org.brightify.hyperdrive.autofactory.AutoFactoryResolveExtension
import org.brightify.hyperdrive.viewmodel.ViewModelIrGenerationExtension
import org.brightify.hyperdrive.viewmodel.ViewModelResolveExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension

@AutoService(ComponentRegistrar::class)
class MultiplatformXComponentRegistrar: ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        messageCollector.report(CompilerMessageSeverity.WARNING, "Hyperdrive MultiplatformX is experimental!")

        val syntheticResolveExtensions = listOf(
            AutoFactoryResolveExtension(),
            ViewModelResolveExtension()
        )

        val irGenerationExtensions = listOf(
            AutoFactoryIrGenerationExtension(),
            ViewModelIrGenerationExtension()
        )

        syntheticResolveExtensions.forEach {
            SyntheticResolveExtension.registerExtension(project, it)
        }

        irGenerationExtensions.forEach {
            IrGenerationExtension.registerExtension(project, it)
        }
    }
}