package org.brightify.hyperdrive.multiplatformx.compose

import androidx.compose.runtime.*
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel

@Composable
fun ManageableViewModel.observeAsState(): State<ManageableViewModel> {
    val result = remember { mutableStateOf(this, neverEqualPolicy()) }
    DisposableEffect(this) {
        val token = this@observeAsState.willChange.addListener {
            result.value = this@observeAsState
        }
        onDispose {
            token.cancel()
        }
    }
    return result
}