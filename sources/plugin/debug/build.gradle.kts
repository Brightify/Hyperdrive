import org.brightify.hyperdrive.configurePlatforms

plugins {
    id("hyperdrive-multiplatform")
    // Include in documentation generation.
    alias(libs.plugins.dokka)
}

tasks.dokkaHtmlPartial.configure {
    moduleName.set("Plugin Debug Annotations (${moduleName.get()})")
}
