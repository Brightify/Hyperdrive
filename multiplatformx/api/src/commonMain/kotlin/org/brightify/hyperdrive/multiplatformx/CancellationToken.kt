package org.brightify.hyperdrive.multiplatformx

class CancellationToken(private val onCancel: () -> Unit) {
    fun cancel() {
        onCancel()
    }
}