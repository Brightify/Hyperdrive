package org.brightify.hyperdrive

import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class KotlinCompilerGradleSubplugin: KotlinCompilerPluginSupportPlugin {

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        return project.provider {
            val hyperdrive = project.extensions.findByType() ?: HyperdriveExtension()
            val multiplatformX = hyperdrive.multiplatformx
            val multiplatformXOptions = if (multiplatformX != null) {
                listOf(
                    option(MultiplatformxCommandLineProcessor.Options.enabled, "true"),
                    option(MultiplatformxCommandLineProcessor.Options.autoFactoryEnabled, multiplatformX.isAutoFactoryEnabled),
                    option(MultiplatformxCommandLineProcessor.Options.viewModelEnabled, multiplatformX.isViewModelEnabled)
                )
            } else {
                listOf(
                    option(MultiplatformxCommandLineProcessor.Options.enabled, "false")
                )
            }

            val krpc = hyperdrive.krpc
//            val krpcOptions = if (krpc != null) {
//                listOf(
//                    option(Options.Krpc.enabled, "true")
//                )
//            } else {
//                listOf(
//                    option(Options.Krpc.enabled, "false")
//                )
//            }
            val krpcOptions = emptyList<SubpluginOption>()

            multiplatformXOptions + krpcOptions
        }
    }

    override fun getCompilerPluginId() = BuildConfig.KOTLIN_PLUGIN_ID
    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(groupId = BuildConfig.KOTLIN_PLUGIN_GROUP, artifactId = BuildConfig.KOTLIN_PLUGIN_NAME, version = BuildConfig.KOTLIN_PLUGIN_VERSION)

    override fun getPluginArtifactForNative(): SubpluginArtifact? =
        SubpluginArtifact(groupId = BuildConfig.KOTLIN_PLUGIN_GROUP, artifactId = BuildConfig.KOTLIN_NATIVE_PLUGIN_NAME, version = BuildConfig.KOTLIN_PLUGIN_VERSION)

    private companion object {
        fun <T> option(from: PluginOption, value: T): SubpluginOption = SubpluginOption(from.optionName, value.toString())
    }
}
