buildscript {
    val isProbablySupportingJetpackCompose: Boolean =
        extra.properties.getOrDefault("android.injected.studio.version", "").toString().toLowerCase().contains("canary") ||
                extra.properties.getOrDefault("android.injected.studio.version", "").toString().toLowerCase().contains("beta") ||
                extra.properties.getOrDefault("enableCompose", "false").toString().toBoolean()

    extra["enableCompose"] = isProbablySupportingJetpackCompose

    val enableCompose: Boolean by extra
    println("Jetpack Compose enabled: $enableCompose")
}
