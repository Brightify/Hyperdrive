---
sidebar_position: 1
title: Overview
---

# MultiplatformX


:::note Installation
To start using **MultiplatformX**, find out how to [**get started**][getting-started-multiplatformx].
:::

## Delegate properties
**Hyperdrive API** provides property delegates that the **Hyperdrive Gradle plugin** then uses to generate boilerplate IR for you to use. To allow all platforms to correctly update based on view model changes, all mutable values should use one of the provided property delegates.

### `published`
The most basic of the delegates, allows the views to observe changes the view model makes to the property. For view models and lists of view models, instead use `managed` and `managedList` respectively.

:::caution
`List`s and `Map`s only capture changes on assignment (i.e. `list = newList`), so mutating them in any other way is not propagated.
:::

### `collected`
Works similarly to `published`, although this one is used for observing a `Flow` or `StateFlow` and mapping the values if needed.

:::tip
Properties declared using the property delegates are mappable, this allows for a property depending on another to be observable as well.

Example:
```kotlin title="BedViewModel.kt"
@ViewModel
class BedViewModel: BaseViewModel() {
    var monstersNearby = published(0)

    val isSleepAvailable by observeMonstersNearby.map { it.isEmpty }
}
```
:::

### `managed` and `managedList`
Due to the way view model hierarchy works in Hyperdrive, it is recommended to use `managed` for a single view model and `managedList` for a list of view models. This automatically *manages* them to make sure that they don't perform any unnecessary work when they aren't yet used in the view hierarchy.

In case you need a view model to eagerly perform work like observe flows in `collected` and perform code in `whileAttached()`, you may use `published` instead, though that is not recommended unless you're really sure what you're doing.

## Annotations
The following annotations are used by the **Hyperdrive Gradle plugin** to generate and modify IR to let you focus on the code and not the surrounding boilerplate.

### `@ViewModel`
Enables generating `observeX` accessors for all Hyperdrive property delegates within the annotated class.

:::caution
The annotated class has to inherit from **`BaseViewModel`**.
:::

```kotlin title="ContactViewModel.kt"
@ViewModel
class ContactViewModel: BaseViewModel() {
    // `observeName` of type `StateFlow<String>` is generated.
    var name by published("Samantha")
    // `observeNumber` of type `StateFlow<String?>` is generated.
    var number: String? by published(null)

    var overview = combine(observeName, observeNumber) { (name, number) ->
        "Name: $name" + number?.let { ", number: $it" } ?: ""
    }
}
```

### `@AutoFactory`
Automatic factory generation for the annotated class or constructor.

By default, the generated factory class is named `Factory` and its method `create`. This factory method then uses the annotated constructor (or the primary constructor if the class is annotated) to create a new instance of the class while leaving the common dependencies up to the dependency injection.

All parameters of the `@AutoFactory` constructor are assumed to be injectable unless annotated `@Provided`.

Usage:
```kotlin title="FriendDetailViewModel.kt"
@AutoFactory
class FriendDetailViewModel(
    private val friendGateway: FriendGateway,
    @Provided
    id: Friend.Id,
): BaseViewModel()
```

The **Hyperdrive Gradle plugin** generates the following equivalent in Kotlin code:
```kotlin
// Inside `FriendDetailViewModel` namespace.
class Factory(
    // All parameters not annotated `@Provided`.
    private val friendGateway: FriendGateway
) {
    // Contains All parameters annotated `@Provided`.
    fun create(id: Friend.Id) = FriendDetailViewModel(friendGateway, id)
}
```

This allows you to pass the factory with the injectable dependencies already provided to view models which then just call `create` with only the necessary values (e.g. an ID to fetch more info). An example would look like this:
```kotlin
dependencyContainer {
    register {
        FriendDetailViewModel.Factory(
            friendGateway = inject(),
        )
    }
    register {
        FriendListViewModel.Factory(
            detailFactory = inject(),
        )
    }
}
```

And then `FriendListViewModel` uses the generated factory to instantiate a child view model without the need to pass all the dependencies.
```kotlin title="FriendListViewModel.kt"
class FriendListViewModel(
    private val detailFactory: FriendDetailViewModel.Factory,
): BaseViewModel() {
    var presentedDetail: FriendDetailViewModel? by managed(null)

    fun openDetail(id: Friend.Id) {
        presentedDetail = detailFactory.create(id)
    }
}
```

:::caution
Annotating multiple constructors or both the class and a constructor is not supported and will produce compilation errors.
:::

### `@NoAutoObserve`

Disables IR modifications on `@Composable` functions that provide automatic `ManageableViewModel` (`BaseViewModel` implements this interface) observing. You might want to do this if you feel this imposes a performance hit on a particularly complex `@Composable` function and there's no way of restructuring it into simpler ones.

[getting-started-multiplatformx]: ../getting-started/intro.md#multiplatformx
