---
sidebar_position: 4
---

# MultiplatformX

Pick and choose from the dependencies described below and then add them to your `build.gradle.kts` file.

```kotlin {5} title="build.gradle.kts"
kotlin {
    sourceSets {
        val module by getting {
            dependencies {
                // Add dependencies here.
            }
        }
    }
}
```

#### API

The main dependency, containing `BaseViewModel`, `AutoFactory` as well as view model properties. Necessary for generating `Factory.create` methods and `observeX` accessors.

```kotlin
api("org.brightify.hyperdrive:multiplatformx-api:0.1.80")
```

#### Compose

Tools specific for Jetpack Compose for Android. This dependency is necessary to automatically observe `BaseViewModel` in `@Composable` functions.

```kotlin
api("org.brightify.hyperdrive:multiplatformx-compose:0.1.80")
```

#### Key-Value

Multiplatform abstraction of key-value storage separated into API as well as an insecure implementation (using `UserDefaults` on iOS and `SharedPreferences` on Android).

```kotlin
// API
api("org.brightify.hyperdrive:multiplatformx-keyvalue:0.1.80")
// Insecure implementation
api("org.brightify.hyperdrive:multiplatformx-keyvalue-insecure-settings:0.1.80")
```
