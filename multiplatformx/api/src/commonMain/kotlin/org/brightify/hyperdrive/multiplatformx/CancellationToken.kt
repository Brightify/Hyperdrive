package org.brightify.hyperdrive.multiplatformx

public class CancellationToken(private val onCancel: () -> Unit) {
    public fun cancel() {
        onCancel()
    }
}