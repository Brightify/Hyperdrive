package org.brightify.hyperdrive.multiplatformx

public class CancellationToken(private val onCancel: () -> Unit) {
    public var isCanceled: Boolean = false
        private set
    
    public fun cancel() {
        check(!isCanceled) { "Canceling an already canceled token is illegal." }
        onCancel()
        isCanceled = true
    }

    public companion object {
        public fun concat(tokens: Iterable<CancellationToken>): CancellationToken {
            return CancellationToken {
                for (token in tokens) {
                    token.cancel()
                }
            }
        }
    }
}

public fun Iterable<CancellationToken>.concat(): CancellationToken = CancellationToken.concat(this)