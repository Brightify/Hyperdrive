package org.brightify.hyperdrive

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create

class BuildSetupPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.apply(plugin = "org.jetbrains.kotlin.multiplatform")
    }
}