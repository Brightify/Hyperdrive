plugins {
    id("hyperdrive-base")
    id("io.github.gradle-nexus.publish-plugin")
}

nexusPublishing {
    repositories {
        sonatype()
    }
}
