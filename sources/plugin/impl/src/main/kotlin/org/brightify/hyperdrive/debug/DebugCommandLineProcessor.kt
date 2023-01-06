package org.brightify.hyperdrive.debug

import com.google.auto.service.AutoService
import org.brightify.hyperdrive.PluginOption
import org.brightify.hyperdrive.krpc.plugin.KrpcCommandLineProcessor
import org.brightify.hyperdrive.krpc.plugin.KrpcConfigurationKeys
import org.jetbrains.kotlin.cli.common.toBooleanLenient
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

@AutoService(CommandLineProcessor::class)
class DebugCommandLineProcessor: CommandLineProcessor {
    companion object {
        const val pluginId: String = "org.brightify.hyperdrive.debug"
    }

    override val pluginId: String = DebugCommandLineProcessor.pluginId

    private val options = listOf(
        Options.enabled,
        Options.disablePrintIR,
        Options.disablePrintKotlinLike,
    )
    private val optionsMap = options.associateBy { it.optionName }
    override val pluginOptions: Collection<AbstractCliOption> = options.map { it.toCliOption() }

    object Options {
        val enabled = PluginOption("enabled", "<true|false>", "")
        val disablePrintIR = PluginOption("disablePrintIR", "<true|false>", "")
        val disablePrintKotlinLike = PluginOption("disablePrintKotlinLike", "<true|false>", "")
    }

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        super.processOption(option, value, configuration)

        when (optionsMap[option.optionName]) {
            Options.enabled -> configuration.putIfNotNull(DebugConfigurationKeys.isEnabled, value.toBooleanLenient())
            Options.disablePrintIR -> configuration.putIfNotNull(DebugConfigurationKeys.disablePrintIR, value.toBooleanLenient())
            Options.disablePrintKotlinLike -> configuration.putIfNotNull(DebugConfigurationKeys.disablePrintKotlinLike, value.toBooleanLenient())
        }
    }
}
