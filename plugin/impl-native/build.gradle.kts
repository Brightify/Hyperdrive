import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    `maven-publish`
}

dependencies {
    implementation(project(":plugin-impl", configuration = "shadow"))
}

tasks.jar {
    from(configurations.runtimeClasspath.map { config ->
        config.map {
            if (it.isDirectory || !it.exists()) it else zipTree(it)
        }
    })
}
