package org.brightify.hyperdrive

import org.brightify.hyperdrive.krpc.plugin.KrpcCommandLineProcessor
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

fun <T> PluginOption.subpluginOption(value: T): SubpluginOption = SubpluginOption(optionName, value.toString())

fun KotlinCompilation<*>.addAllDependencies() {
    kotlinOptions.freeCompilerArgs += BuildConfig.KOTLIN_PLUGIN_DEPENDENCIES.map {
        "-Xplugin=${it}"
    }
}
