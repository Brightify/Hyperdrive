package org.brightify.hyperdrive

import org.brightify.hyperdrive.impl.BlockCancellationToken

/**
 * Implemented by classes that contain some form of cancellation logic.
 */
public interface CancellationToken {
    /**
     * Whether the `cancel()` method has already been called
     * and the cancellation logic successfully executed.
     */
    public val isCanceled: Boolean

    /**
     * Execute the cancellation logic.
     */
    public fun cancel()

    public companion object {
        /**
         * Create a new [CancellationToken] with a closure as the cancellation logic.
         */
        public operator fun invoke(onCancel: () -> Unit): CancellationToken {
            return BlockCancellationToken(onCancel)
        }

        /**
         * Combine provided [CancellationToken] instances into one.
         */
        public fun concat(tokens: Iterable<CancellationToken>): CancellationToken {
            return CancellationToken {
                for (token in tokens) {
                    token.cancel()
                }
            }
        }
    }
}

/**
 * Combine all contained [CancellationToken] instances into one.
 */
public fun Iterable<CancellationToken>.concat(): CancellationToken = CancellationToken.concat(this)
