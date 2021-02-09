package org.brightify.hyperdrive

import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import javax.inject.Inject

@AutoService(KotlinCompilerPluginSupportPlugin::class)
class KotlinCompilerGradleSubplugin @Inject internal constructor(
    private val registry: ToolingModelBuilderRegistry
): KotlinCompilerPluginSupportPlugin {

    override fun apply(project: Project) {

    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        return project.provider { emptyList<SubpluginOption>() }
    }

    override fun getCompilerPluginId() = BuildConfig.KOTLIN_PLUGIN_ID
    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(groupId = BuildConfig.KOTLIN_PLUGIN_GROUP, artifactId = BuildConfig.KOTLIN_PLUGIN_NAME, version = BuildConfig.KOTLIN_PLUGIN_VERSION)

    override fun getPluginArtifactForNative(): SubpluginArtifact? =
        SubpluginArtifact(groupId = BuildConfig.KOTLIN_PLUGIN_GROUP, artifactId = BuildConfig.KOTLIN_NATIVE_PLUGIN_NAME, version = BuildConfig.KOTLIN_PLUGIN_VERSION)
}
