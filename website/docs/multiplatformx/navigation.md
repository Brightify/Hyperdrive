---
sidebar_position: 2
---

# Navigation
Hyperdrive view model navigation uses a tree-like structure instead of Jetpack Compose's `NavController` path structure.

The view models presented are *attached* into the navigation tree and their observing in `collect` as well as any work in `whileAttached()` is performed.

[TODO: Hyperdrive navigation is being upgraded to support jumping to a specific node in the tree (e.g. user detail with an ID from a notification).]

The parent view model *presents* the child view models and has full control over whether they're shown or not:
```kotlin title="EventListViewModel.kt"
class EventListViewModel: BaseViewModel() {
    var presentedDetail: EventDetailViewModel? by managed(null)

    fun openDetail() {
        presentedDetail = EventDetailViewModel()
    }
}
```

In **SwiftUI**, it's enough to use **`SwitchingNavigationLink`** with the presented view model as its parameter along with the content that represents the view to present.
```swift title="EventListView.swift"
struct EventListView: View {
    @ObservedObject
    private(set) var viewModel: EventListViewModel

    var body: some View {
        NavigationView {
            ZStack {
                SwitchingNavigationLink(
                    selection: $viewModel.presentedDetail,
                    content: { EventDetailView(viewModel: $0) }
                )

                // View layout.
            }
        }
    }
```

**Jetpack Compose** doesn't use `NavController` and instead Hyperdrive's `NavigationController` is utilized.
```kotlin title="EventListView.kt"
@Composable
fun EventListView(viewModel: EventListViewModel, modifier: Modifier) {
    if (viewModel.presentedDetail == null) {
        // View layout.
    }

    NavigationController.Pushed(present = viewModel::presentedDetail) {
        EventListView(viewModel = it, modifier = modifier)
    }
}
```

The current view is shown if `presentedDetail` is null, otherwise `NavigationController` automatically presents the provided view.
