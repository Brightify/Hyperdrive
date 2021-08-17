buildscript {
    val androidStudioVersionSuffixes = listOf(
        "canary",
        "dev",
        "beta",
        "final",
    )
    val isProbablySupportingJetpackCompose: Boolean = androidStudioVersionSuffixes.any {
        extra.properties.getOrDefault("android.injected.studio.version", "").toString().toLowerCase().contains(it)
    } || extra.properties.getOrDefault("enableCompose", "false").toString().toBoolean()

    extra["enableCompose"] = isProbablySupportingJetpackCompose

    val enableCompose: Boolean by extra
    println("Jetpack Compose enabled: $enableCompose")
}
