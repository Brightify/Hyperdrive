package org.brightify.hyperdrive.multiplatformx.util

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import org.brightify.hyperdrive.multiplatformx.Lifecycle

public class AsyncQueue<T>(
    private val overflowPolicy: OverflowPolicy,
    private val lifecycle: Lifecycle,
    private val action: suspend (T) -> Unit,
) {
    private var runningJob: Job? = null

    private val queue = MutableStateFlow(emptyList<T>())
    private val isRunningAction = MutableStateFlow(false)

    public suspend fun awaitIdle() {
        isRunningAction.filterNot { it }.first()
    }

    init {
        lifecycle.whileAttached {
            while (isActive) {
                // Wait for non-empty queue
                queue.filter { it.isNotEmpty() }.first()
                isRunningAction.value = true

                do {
                    val item = queue.getAndUpdate { it.drop(1) }.firstOrNull() ?: break

                    action(item)
                } while (isActive)

                isRunningAction.value = false
            }
        }
    }

    public fun push(next: T) {
        queue.update {
            when (overflowPolicy) {
                OverflowPolicy.Concat -> it + next
                OverflowPolicy.Conflate -> listOf(next)
                OverflowPolicy.Drop -> it.ifEmpty {
                    listOf(next)
                }
            }
        }
    }

    // TODO: Add support for `Latest`
    public enum class OverflowPolicy {
        Concat, Conflate, Drop
    }
}