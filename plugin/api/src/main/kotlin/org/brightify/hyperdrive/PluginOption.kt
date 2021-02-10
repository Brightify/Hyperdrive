package org.brightify.hyperdrive

import org.jetbrains.kotlin.compiler.plugin.CliOption

data class PluginOption(
    val optionName: String,
    val valueDescription: String,
    val description: String,
    val isRequired: Boolean = false,
    val allowMultipleOccurrences: Boolean = false
) {
    fun toCliOption() = CliOption(
        optionName,
        valueDescription,
        description,
        isRequired,
        allowMultipleOccurrences
    )
}
