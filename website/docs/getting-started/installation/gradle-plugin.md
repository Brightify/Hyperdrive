---
sidebar_position: 2
---

# Gradle plugin

While optional, the **Hyperdrive Gradle plugin** does all the legwork of generating the IR necessary to make multiplatform as seamless as it can be.

Simply add the following code to the appropriate files:

```kotlin title="settings.gradle.kts"
pluginManagement {
    plugins {
        id("org.brightify.hyperdrive") version "0.1.80"
    }
}
```

```kotlin title="build.gradle.kts"
plugins {
    id("org.brightify.hyperdrive")
}

hyperdrive {
    // MultiplatformX
    multiplatformx()

    // kRPC
    krpc()
}
```

:::tip
This is enough to automatically run the Hyperdrive IR generator with sensible defaults. Find out how to [customize the setup here](#customizing).
:::

### Customizing {#customizing}

The default values are as follows:

```kotlin title="build.gradle.kts"
hyperdrive {
    multiplatformx {
        // Generate `Factory.create` methods for classes annotated `@AutoFactory`.
        isAutoFactoryEnabled = true

        // Generate accessors for Hyperdrive observable properties in classes annotated `@ViewModel`.
        isViewModelEnabled = true

        // Modify `@Composable` functions' IR to automatically observe `BaseViewModel` without the need for `observeAsState()`.
        isComposableAutoObserveEnabled = true
    }

    krpc {
        // For debugging purposes.
        printIR = false
        printKotlinLike = false
    }
}
```
