import org.brightify.hyperdrive.configurePlatforms

plugins {
    id("hyperdrive-multiplatform")
}

tasks.dokkaHtmlPartial.configure {
    moduleName.set("Plugin Debug Annotations (${moduleName.get()})")
}
