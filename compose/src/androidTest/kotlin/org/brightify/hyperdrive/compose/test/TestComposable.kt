package org.brightify.hyperdrive.compose.test

import androidx.compose.runtime.Composable
import androidx.compose.material.Button
import androidx.compose.material.Text
import org.brightify.hyperdrive.BaseViewModel
import org.brightify.hyperdrive.ViewModel
import org.brightify.hyperdrive.compose.observeAsState

@ViewModel
class TogglingVM: BaseViewModel() {
    var index by published(0)

    fun increment() {
        index += 1
    }
}

@Composable
fun TestView(viewModel: TogglingVM) {
    Button(onClick = viewModel::increment) {
        Text("${viewModel.index}")
    }
}

@Composable
fun NoAutoObserveTestView(viewModel: TogglingVM) {
    val _viewModel = viewModel.observeAsState()
    Button(onClick = viewModel::increment) {
        Text("${_viewModel.value.index}")
    }
}
