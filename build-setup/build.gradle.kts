plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    // Adds the version catalog to the convention plugins classpath
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    implementation(libs.intellij.gradle.plugin) {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(libs.dokka.gradle.plugin) {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(libs.nexusPublish.plugin)
    compileOnly(gradleApi())
    compileOnly(localGroovy())
}
