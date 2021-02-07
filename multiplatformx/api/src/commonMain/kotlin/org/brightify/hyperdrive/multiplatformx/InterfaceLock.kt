package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.observeOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KProperty

class InterfaceLock(
    private val scope: CoroutineScope,
    private val group: Group = Group(),
) {
    private val lock = Mutex()
    private val mutableState = MutableStateFlow<State>(State.Idle)
    val observeState: StateFlow<State> = mutableState
    var state: State by mutableState
        private set
    val isLocked: Boolean
        get() = state == State.Loading

    /**
     * While the supplied `work` is running, this lock is considered taken and all other invocations of this method will just return, doing nothing.
     */
    fun runExclusively(work: suspend () -> Unit) {
        scope.launch {
            group.runExclusively {
                mutableState.value = State.Loading
                mutableState.value = try {
                    work()

                    State.Idle
                } catch (t: Throwable) {
                    State.Failed(t)
                }
            }
        }
    }

    class Group {
        private val lock = Mutex()

        private val mutableIsLocked = MutableStateFlow(false)
        val observeIsLocked: StateFlow<Boolean> = mutableIsLocked
        var isLocked: Boolean by mutableIsLocked
            private set

        suspend fun runExclusively(work: suspend () -> Unit) {
            lock.withLock {
                if (isLocked) {
                    return
                }

                mutableIsLocked.value = true
            }

            work()

            lock.withLock {
                mutableIsLocked.value = false
            }
        }
    }

    sealed class State {
        object Loading: State()
        class Failed(val throwable: Throwable): State()
        object Idle: State()
    }
}

@Deprecated("Renamed for better clarity.", ReplaceWith("runExclusively(work)"))
fun InterfaceLock.first(work: suspend () -> Unit) = runExclusively(work)
