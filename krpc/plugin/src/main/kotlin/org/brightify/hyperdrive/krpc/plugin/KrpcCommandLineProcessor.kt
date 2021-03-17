package org.brightify.hyperdrive.krpc.plugin

import com.google.auto.service.AutoService
import org.brightify.hyperdrive.BuildConfig
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor

@AutoService(CommandLineProcessor::class)
class KrpcCommandLineProcessor: CommandLineProcessor {
    override val pluginId: String = BuildConfig.KOTLIN_PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> = emptyList()
}