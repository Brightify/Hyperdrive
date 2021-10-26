package org.brightify.hyperdrive.multiplatformx.compose

import androidx.compose.runtime.*
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.ObservableObject
import org.brightify.hyperdrive.multiplatformx.property.MutableObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

/**
 * Observe a view model as its properties change to update the view.
 *
 * Equivalent to [ObservableProperty.observeAsState] for observing all changes in a view model.
 */
@NoAutoObserve
@Composable
fun <T: ManageableViewModel> T.observeAsState(): State<T> {
    val result = remember { mutableStateOf(this, neverEqualPolicy()) }
    val listener = remember {
        object: ObservableObject.ChangeTracking.Listener {
            override fun onObjectDidChange() {
                result.value = this@observeAsState
            }
        }
    }
    DisposableEffect(this) {
        val token = changeTracking.addListener(listener)
        result.value = this

        onDispose {
            token.cancel()
        }
    }
    return result
}

/**
 * Observe a view model property as it changes to update the view.
 *
 * Equivalent to [collectAsState] for [ObservableProperty].
 */
@NoAutoObserve
@Composable
fun <T> ObservableProperty<T>.observeAsState(): State<T> {
    val result = remember { mutableStateOf(value, neverEqualPolicy()) }
    val listener = remember {
        object: ObservableProperty.Listener<T> {
            override fun valueDidChange(oldValue: T, newValue: T) {
                result.value = newValue
            }
        }
    }
    DisposableEffect(this) {
        val token = addListener(listener)
        result.value = value

        onDispose {
            token.cancel()
        }
    }
    return result
}
