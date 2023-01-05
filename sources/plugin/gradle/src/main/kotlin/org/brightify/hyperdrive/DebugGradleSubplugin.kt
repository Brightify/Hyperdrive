package org.brightify.hyperdrive

import org.brightify.hyperdrive.debug.DebugCommandLineProcessor
import org.brightify.hyperdrive.krpc.plugin.KrpcCommandLineProcessor
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class DebugGradleSubplugin: KotlinCompilerPluginSupportPlugin {

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    // TODO: Apply required dependencies?
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        return project.provider {
            val hyperdrive = project.extensions.findByType<HyperdriveExtension>() ?: HyperdriveExtension()

            val debug = hyperdrive.debug
            if (debug != null) {
                listOf(
                    DebugCommandLineProcessor.Options.enabled.subpluginOption("true"),
                    DebugCommandLineProcessor.Options.disablePrintIR.subpluginOption(debug.disablePrintIR.toString()),
                    DebugCommandLineProcessor.Options.disablePrintKotlinLike.subpluginOption(debug.disablePrintKotlinLike.toString())
                )
            } else {
                listOf(
                    DebugCommandLineProcessor.Options.enabled.subpluginOption("false")
                )
            }
        }
    }

    override fun getCompilerPluginId() = DebugCommandLineProcessor.pluginId
    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(groupId = BuildConfig.KOTLIN_PLUGIN_GROUP, artifactId = BuildConfig.KOTLIN_PLUGIN_NAME, version = BuildConfig.KOTLIN_PLUGIN_VERSION)
}
