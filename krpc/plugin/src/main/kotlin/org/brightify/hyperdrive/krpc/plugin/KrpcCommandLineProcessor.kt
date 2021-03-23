package org.brightify.hyperdrive.krpc.plugin

import com.google.auto.service.AutoService
import org.brightify.hyperdrive.PluginOption
import org.jetbrains.kotlin.cli.common.toBooleanLenient
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

@AutoService(CommandLineProcessor::class)
class KrpcCommandLineProcessor: CommandLineProcessor {
    companion object {
        const val pluginId: String = "org.brightify.hyperdrive.krpc"
    }

    override val pluginId: String = KrpcCommandLineProcessor.pluginId

    private val options = listOf(
        Options.enabled
    )
    private val optionsMap = options.map { it.optionName to it }.toMap()
    override val pluginOptions: Collection<AbstractCliOption> = options.map { it.toCliOption() }

    object Options {
        val enabled = PluginOption("enabled", "<true|false>", "")
    }

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        super.processOption(option, value, configuration)

        when (optionsMap[option.optionName]) {
            Options.enabled -> configuration.putIfNotNull(KrpcConfigurationKeys.isEnabled, value.toBooleanLenient())
        }
    }
}