package org.brightify.hyperdrive.multiplatformx

import org.brightify.hyperdrive.multiplatformx.impl.BlockCancellationToken

public interface CancellationToken {
    public val isCanceled: Boolean

    public fun cancel()

    public companion object {
        public operator fun invoke(onCancel: () -> Unit): CancellationToken {
            return BlockCancellationToken(onCancel)
        }

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

