@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.brightify.hyperdrive.multiplatformx.util.getValue
import org.brightify.hyperdrive.multiplatformx.util.setValue

/**
 * Locking for user interface operations.
 *
 * Use an instance of this class to ensure user interactions are ignored while a user-initiated operation is already running.
 *
 * Each instance of [InterfaceLock] is a member of a [Group] and if no [group] parameter is provided to the constructor a new group is
 * created by default. Only a single operation can be running across all [InterfaceLock] instances in the same group. Additional operations
 * are not run until the currently running one completes.
 *
 * Since the [runExclusively] is not blocking or suspending, you can monitor the progress using the [state] and [observeState] properties.
 */
public class InterfaceLock(
    // TODO: Research if we should be using a "global" scope, or if we should use a "lifecycle" scope instead.
    private val lifecycle: Lifecycle,
    private val group: Group = Group(),
) {
    private val mutableState = MutableStateFlow<State>(State.Idle)
    public val observeState: StateFlow<State> = mutableState
    public var state: State by mutableState
        private set
    public val isLocked: Boolean
        get() = state == State.Running

    /**
     * While the supplied `work` is running, this lock is considered taken and all other invocations of this method will just return, doing nothing.
     */
    public fun runExclusively(work: suspend () -> Unit) {
        lifecycle.runOnceIfAttached {
            group.runExclusively {
                mutableState.value = State.Running
                mutableState.value = try {
                    work()

                    State.Idle
                } catch (cancellation: CancellationException) {
                    throw cancellation
                }
                // TODO: It might be unsafe to catch any Throwable. Please research.
                catch (t: Throwable) {
                    State.Failed(t)
                }
            }
        }
    }

    /**
     * Resets the current state to [Idle][State.Idle] if it's currently [Failed][State.Failed].
     */
    public fun resetFailedState(): Boolean {
        val currentState = mutableState.value
        return if (currentState is State.Failed) {
            mutableState.compareAndSet(currentState, State.Idle)
        } else {
            false
        }
    }

    public class Group {
        private val mutableIsOperationRunning = MutableStateFlow(false)
        public val observeIsOperationRunning: StateFlow<Boolean> = mutableIsOperationRunning
        public var isOperationRunning: Boolean by mutableIsOperationRunning
            private set

        public suspend fun runExclusively(work: suspend () -> Unit) {
            if (!mutableIsOperationRunning.compareAndSet(expect = false, update = true)) {
                return
            }

            try {
                work()
            } finally {
                mutableIsOperationRunning.value = false
            }
        }
    }

    public sealed class State {
        public object Running: State()
        public class Failed(public val throwable: Throwable): State()
        public object Idle: State()
    }
}
