---
sidebar_position: 1
---

# Getting Started

###### This page is an overview of the Hyperdrive ecosystem as well as a guide to just the right amount of dependencies.

---

Hyperdrive consists of two main modules — MultiplatformX and kRPC.

### MultiplatformX {#multiplatformx}

Seamless iOS and Android multiplatform view model integration with **SwiftUI** and **Jetpack Compose**, all in shared Kotlin code.

> Head over to [**Installation**][installation] if you're keen on the idea of reusing one view model for all platforms.

Defining a single `BaseViewModel` while using the provided delegate properties makes for a frictionless integration on all platforms. The **delegates** provide a clean and synchronous API while still leaving the option to observe each property individually when the need arises.

:::tip
The view models should bear almost all the view logic, this allows for **sharing all code except the platform-specific layout**.
:::

#### Simplified Example

A simple Hyperdrive view model looks like this:

```kotlin title="NewAccountViewModel.kt"
@ViewModel
@AutoFactory
class NewAccountViewModel: BaseViewModel() {
    var name by published("")

    val greeting by observeName.map { name ->
        if (name.isEmpty) {
            "Hello!"
        } else {
            "Hello, $it!"
        }
    }

    val isCreatingAccount by collected(instanceLock.isLoading)

    fun createAccountTapped() = instanceLock.runExclusively {
        // This lock provides us with a suspending scope and prevents duplicate actions.
        delay(500)
    }
}
```

In **SwiftUI**, `@ObservedObject` is used on the view model to automatically update the UI with new values.

```swift title="NewAccountView.swift"
struct NewAccountView {
    @ObservedObject
    var viewModel: NewAccountViewModel

    var body: some View {
        VStack {
            Text(viewModel.greeting)

            TextField("Name", text: $viewModel.name)

            if viewModel.isCreatingAccount {
                ProgressView()
            } else {
                Button("Create Account", action: viewModel.createAccountTapped)
            }
        }
    }
}
```

**Jetpack Compose**, thanks to the **Hyperdrive Gradle plugin** allows for a much more straight-forward code without the need for `collectAsState()` for every property you need to observe.

```kotlin title="NewAccountView.kt"
@Composable
fun NewAccountView(viewModel: NewAccountViewModel) {
    Column {
        Text(viewModel.greeting)

        TextField(
            value = message,
            onValueChange = { viewModel.name = it },
        )

        if (viewModel.isCreatingAccount) {
            CircularProgressIndicator()
        } else {
            Button(onClick = viewModel.createAccountTapped) {
                Text("Create Account")
            }
        }
    }
}
```

:::tip
For more complex variations of the Hyperdrive view model, check out the [MultiplatformX documentation][multiplatformx-intro].
:::

[installation]: ./installation/gradle-plugin
[navigation-tutorial]: ../multiplatformx/navigation
[multiplatformx-intro]: ../multiplatformx/intro
