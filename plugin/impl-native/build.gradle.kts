import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    `maven-publish`
}

dependencies {
    implementation(project(":plugin-impl", configuration = "shadow"))
}

tasks.withType<org.gradle.jvm.tasks.Jar> {
    from(configurations.runtimeClasspath.map { config ->
        config.map {
            if (it.isDirectory) it else zipTree(it)
        }
    })
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
