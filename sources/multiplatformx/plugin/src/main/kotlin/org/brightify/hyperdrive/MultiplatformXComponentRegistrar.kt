package org.brightify.hyperdrive

import org.brightify.hyperdrive.autofactory.AutoFactoryIrGenerationExtension
import org.brightify.hyperdrive.autofactory.AutoFactoryResolveExtension
import org.brightify.hyperdrive.viewmodel.ViewModelIrGenerationExtension
import org.brightify.hyperdrive.viewmodel.ViewModelResolveExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.extensions.LoadingOrder
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension

object MultiplatformXConfigurationKeys {
    val isEnabled = CompilerConfigurationKey<Boolean>("enabled")

    object AutoFactory {
        val isEnabled = CompilerConfigurationKey<Boolean>("autofactory.enabled")
    }

    object ViewModel {
        val isEnabled = CompilerConfigurationKey<Boolean>("viewmodel.enabled")
        val isComposableAutoObserveEnabled = CompilerConfigurationKey<Boolean>("viewmodel.composable-auto-observe.enabled")
    }
}

class MultiplatformXComponentRegistrar: ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        val isEnabled = configuration.getBoolean(MultiplatformXConfigurationKeys.isEnabled)
        if (!isEnabled) {
            return
        }

        val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        messageCollector.report(CompilerMessageSeverity.WARNING, "Hyperdrive MultiplatformX is experimental!")

        if (configuration.getBoolean(MultiplatformXConfigurationKeys.AutoFactory.isEnabled)) {
            registerAutoFactoryExtensions(project)
        }

        if (
            configuration.getBoolean(MultiplatformXConfigurationKeys.ViewModel.isEnabled) ||
            configuration.getBoolean(MultiplatformXConfigurationKeys.ViewModel.isComposableAutoObserveEnabled)
        ) {
            registerViewModelExtensions(
                project,
                messageCollector,
                configuration.getBoolean(MultiplatformXConfigurationKeys.ViewModel.isEnabled),
                configuration.getBoolean(MultiplatformXConfigurationKeys.ViewModel.isComposableAutoObserveEnabled),
            )
        }
    }

    private fun registerAutoFactoryExtensions(project: MockProject) {
        SyntheticResolveExtension.registerExtension(project, AutoFactoryResolveExtension())
        IrGenerationExtension.registerExtension(project, AutoFactoryIrGenerationExtension())
    }

    private fun registerViewModelExtensions(
        project: MockProject,
        messageCollector: MessageCollector,
        isViewModelEnabled: Boolean,
        isComposableAutoObserveEnabled: Boolean,
    ) {
        SyntheticResolveExtension.registerExtension(project, ViewModelResolveExtension())
        val irGenerationExtension = ViewModelIrGenerationExtension(
            messageCollector,
            isViewModelEnabled,
            isComposableAutoObserveEnabled,
        )
        @Suppress("UnstableApiUsage")
        project.extensionArea.getExtensionPoint(IrGenerationExtension.extensionPointName)
            .registerExtension(irGenerationExtension, LoadingOrder.FIRST, project)
    }
}
