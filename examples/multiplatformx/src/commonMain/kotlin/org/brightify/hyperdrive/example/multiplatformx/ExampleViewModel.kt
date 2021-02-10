package org.brightify.hyperdrive.example.multiplatformx

import org.brightify.hyperdrive.multiplatformx.AutoFactory
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.Provided
import org.brightify.hyperdrive.multiplatformx.ViewModel

@ViewModel
@AutoFactory
class ExampleViewModel(
    private val a: String,
    @Provided
    private val b: Int,
): BaseViewModel() {

    val x: String? by published(null)

    init {
        observeX
    }

}