buildscript {
    val androidStudioVersionSuffixes = listOf(
        "canary",
        "dev",
        "beta",
        "final",
        "patch",
    )
    val isProbablySupportingJetpackCompose: Boolean = extra.properties.containsKey("forceEnableCompose") || androidStudioVersionSuffixes.any {
        extra.properties.getOrDefault("android.injected.studio.version", "").toString().toLowerCase().contains(it)
    }

    extra["enableCompose"] = isProbablySupportingJetpackCompose

    val enableCompose: Boolean by extra
    println("Jetpack Compose enabled: $enableCompose")
}
