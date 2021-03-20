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
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object MultiplatformXConfigurationKeys {
    val isEnabled = CompilerConfigurationKey<Boolean>("enabled")

    object AutoFactory {
        val isEnabled = CompilerConfigurationKey<Boolean>("autofactory.enabled")
    }

    object ViewModel {
        val isEnabled = CompilerConfigurationKey<Boolean>("viewmodel.enabled")
    }
}

@AutoService(ComponentRegistrar::class)
class MultiplatformXComponentRegistrar: ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        val isEnabled = configuration.getBoolean(MultiplatformXConfigurationKeys.isEnabled)
        if (!isEnabled) {
            return
        }

        val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        messageCollector.report(CompilerMessageSeverity.WARNING, "Hyperdrive MultiplatformX is experimental!")

        if (configuration.getBoolean(MultiplatformXConfigurationKeys.AutoFactory.isEnabled)) {
            registerAutoFactoryExtensions(project, configuration)
        }

        if (configuration.getBoolean(MultiplatformXConfigurationKeys.ViewModel.isEnabled)) {
            registerViewModelExtensions(project, configuration)
        }
    }

    private fun registerAutoFactoryExtensions(project: MockProject, configuration: CompilerConfiguration) {
        SyntheticResolveExtension.registerExtension(project, AutoFactoryResolveExtension())
        IrGenerationExtension.registerExtension(project, AutoFactoryIrGenerationExtension())
    }

    private fun registerViewModelExtensions(project: MockProject, configuration: CompilerConfiguration) {
        SyntheticResolveExtension.registerExtension(project, ViewModelResolveExtension())
        IrGenerationExtension.registerExtension(project, ViewModelIrGenerationExtension())
    }
}