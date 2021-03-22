package org.brightify.hyperdrive

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create

class HyperdriveGradlePlugin: Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<HyperdriveExtension>("hyperdrive")
        project.apply<KrpcGradleSubplugin>()
        project.apply<MultiplatformXGradleSubplugin>()
    }
}