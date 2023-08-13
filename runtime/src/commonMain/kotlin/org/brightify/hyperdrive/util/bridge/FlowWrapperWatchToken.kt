@file:Suppress("unused")

package org.brightify.hyperdrive.util.bridge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.brightify.hyperdrive.dispatcher

public class FlowWrapperWatchToken private constructor() {

    public companion object {
        public operator fun <T> invoke(
            flow: Flow<T>,
            scope: CoroutineScope = MainScope(),
            onNext: (T) -> Unit,
            onError: (Exception) -> Unit,
            onCompleted: () -> Unit
        ): FlowWrapperWatchToken {
            val token = FlowWrapperWatchToken()

            token.job = scope.launch {
                try {
                    token.awaitNextRequested()

                    flow.collect {
                        onNext(it)

                        token.awaitNextRequested()
                    }

                    onCompleted()
                } catch (e: CancellationException) {
                    onCompleted()
                } catch (e: Exception) {
                    e.printStackTrace()
                    onError(e)
                }
            }

            return token
        }
    }

    internal lateinit var job: Job

    private var deferred = CompletableDeferred<Unit>()

    internal suspend fun awaitNextRequested() {
        deferred.await()

        deferred = CompletableDeferred()
    }

    public fun requestNext() {
        deferred.complete(Unit)
    }

    public fun cancel() {
        job.cancel()
    }
}
