package org.brightify.hyperdrive

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.cli.common.toBooleanLenient
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.utils.keysToMap

@AutoService(CommandLineProcessor::class)
class MultiplatformxCommandLineProcessor: CommandLineProcessor {
    companion object {
        const val pluginId: String = "org.brightify.hyperdrive.multiplatformx"
    }

    override val pluginId: String = MultiplatformxCommandLineProcessor.pluginId

    private val options = listOf(
        Options.enabled,
        Options.autoFactoryEnabled,
        Options.viewModelEnabled
    )
    private val optionsMap = options.map { it.optionName to it }.toMap()
    override val pluginOptions: Collection<AbstractCliOption> = options.map { it.toCliOption() }

    object Options {
        val enabled = PluginOption("enabled", "<true|false>", "")
        val autoFactoryEnabled = PluginOption("autofactory.enabled", "<true|false>", "")
        var viewModelEnabled = PluginOption("viewmodel.enabled", "<true|false>", "")
    }

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        super.processOption(option, value, configuration)

        when (optionsMap[option.optionName]) {
            Options.enabled -> configuration.putIfNotNull(MultiplatformXConfigurationKeys.isEnabled, value.toBooleanLenient())
            Options.autoFactoryEnabled -> configuration.putIfNotNull(MultiplatformXConfigurationKeys.AutoFactory.isEnabled, value.toBooleanLenient())
            Options.viewModelEnabled -> configuration.putIfNotNull(MultiplatformXConfigurationKeys.ViewModel.isEnabled, value.toBooleanLenient())
        }
    }
}