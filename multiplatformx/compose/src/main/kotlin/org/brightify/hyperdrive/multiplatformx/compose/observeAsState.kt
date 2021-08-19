package org.brightify.hyperdrive.multiplatformx.compose

import androidx.compose.runtime.*
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.ObservableObject
import org.brightify.hyperdrive.multiplatformx.property.MutableObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty

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
        onDispose {
            token.cancel()
        }
    }
    return result
}

@NoAutoObserve
@Composable
fun <T> ObservableProperty<T>.observeAsState(): State<T> {
    val result = remember { mutableStateOf(value, neverEqualPolicy()) }
    val listener = remember {
        object: ObservableProperty.ValueChangeListener<T> {
            override fun valueDidChange(oldValue: T, newValue: T) {
                result.value = newValue
            }
        }
    }
    DisposableEffect(this) {
        val token = addListener(listener)

        onDispose {
            token.cancel()
        }
    }
    return result
}
