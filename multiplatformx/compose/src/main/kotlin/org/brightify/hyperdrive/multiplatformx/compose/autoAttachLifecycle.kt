package org.brightify.hyperdrive.multiplatformx.compose

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import org.brightify.hyperdrive.multiplatformx.ManageableObject

@Composable
fun <T: ManageableObject> T.withManagedLifecycle(): T {
    manageLifecycle()
    return this
}

@SuppressLint("ComposableNaming")
@Composable
fun ManageableObject.manageLifecycle() {
    val scope = rememberCoroutineScope()

    DisposableEffect(this, scope) {
        lifecycle.attach(scope)
        onDispose {
            lifecycle.detach()
        }
    }
}