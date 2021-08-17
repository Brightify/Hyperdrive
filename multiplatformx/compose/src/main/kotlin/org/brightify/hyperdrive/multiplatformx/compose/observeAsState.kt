package org.brightify.hyperdrive.multiplatformx.compose

import androidx.compose.runtime.*
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.ObservableObject
import org.brightify.hyperdrive.multiplatformx.property.MutableObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty

@NoAutoObserve
@Composable
fun <T: ManageableViewModel> T.observeAsState(): State<T> {
    val result = remember { mutableStateOf(this, neverEqualPolicy()) }
    DisposableEffect(this) {
        val token = this@observeAsState.changeTracking.addListener(
            object: ObservableObject.ChangeTracking.Listener {
                override fun onObjectDidChange() {
                    result.value = this@observeAsState
                }
            }
        )
        onDispose {
            token.cancel()
        }
    }
    return result
}

@NoAutoObserve
@Composable
fun <T> ViewModelProperty<T>.observeAsState(): State<T> {
    val result = remember { mutableStateOf(value, neverEqualPolicy()) }
    DisposableEffect(this) {
        val token = addListener(object: ObservableProperty.ValueChangeListener<T> {
            override fun valueDidChange(oldValue: T, newValue: T) {
                result.value = newValue
            }
        })

        onDispose {
            token.cancel()
        }
    }
    return result
}
