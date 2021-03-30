package org.brightify.hyperdrive.example.krpc

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import org.brightify.hyperdrive.Logger
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_MSEC
import platform.darwin.NSObject
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import kotlin.coroutines.CoroutineContext

@OptIn(InternalCoroutinesApi::class)
internal actual fun dispatcher(): CoroutineDispatcher = UI

val logger = Logger<CoroutineDispatcher>()

@InternalCoroutinesApi
private object UI: CoroutineDispatcher(), Delay {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        logger.trace { "Will dispatch with $context - block" }
        val queue = dispatch_get_main_queue()
        dispatch_async(queue) {
            logger.trace { "Will run block" }
            block.run()
            logger.trace { "Did run block" }
        }
    }

    override fun scheduleResumeAfterDelay(
        timeMillis: Long,
        continuation: CancellableContinuation<Unit>
    ) {
        logger.trace { "Will schedule resume after delay" }
        val queue = dispatch_get_main_queue()

        val time = dispatch_time(DISPATCH_TIME_NOW, (timeMillis * NSEC_PER_MSEC.toLong()))
        dispatch_after(time, queue) {
            with(continuation) {
                resumeUndispatched(Unit)
            }
        }
    }
}
