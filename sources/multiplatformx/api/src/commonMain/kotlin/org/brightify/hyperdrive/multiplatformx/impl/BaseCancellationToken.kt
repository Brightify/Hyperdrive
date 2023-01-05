package org.brightify.hyperdrive.multiplatformx.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken

internal abstract class BaseCancellationToken(private val onCancel: () -> Unit): CancellationToken {
    final override var isCanceled: Boolean = false
        private set

    override fun cancel() {
        check(!isCanceled) { "Canceling an already canceled token is illegal." }
        onWillCancel()
        onCancel()
        isCanceled = true
        onDidCancel()
    }

    protected open fun onWillCancel() { }

    protected open fun onDidCancel() { }
}