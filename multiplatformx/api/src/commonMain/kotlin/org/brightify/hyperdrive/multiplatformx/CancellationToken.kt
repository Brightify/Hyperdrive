package org.brightify.hyperdrive.multiplatformx

public class CancellationToken(private val onCancel: () -> Unit) {
    public fun cancel() {
        onCancel()
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