package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

fun <T: Any> NonNullFlowWrapper<T>.collectWhileAttached(lifecycle: Lifecycle, collection: (T) -> Unit) {
    lifecycle.whileAttached {
        collect {
            collection(it)
        }
    }
}

fun Lifecycle.attachToMultiplatformGlobalScope() {
    attach(MultiplatformGlobalScope)
}

/**
 * This class is not thread-safe. Attaching and detaching from scopes should be performed from the main thread.
 */
class Lifecycle {
    private val children = mutableSetOf<Lifecycle>()
    private var state: State = State.Detached

    val isAttached: Boolean
        get() = state is State.Attached

    private val listeners = mutableMapOf<ListenerRegistration.Kind, MutableSet<ListenerRegistration>>()
    private val whileAttachedRunners = mutableSetOf<suspend CoroutineScope.() -> Unit>()

    private val attachedScope: CoroutineScope?
        get() = when(val state = state) {
            State.Detached -> null
            is State.Attached -> state.scope
        }

    private var activeJobs = mutableSetOf<Job>()
    /**
     * Attach this lifecycle instance to a coroutine scope. Attaching an already attached scope will detach it from the previous one.
     */
    fun attach(scope: CoroutineScope) {
        if (isAttached) {
            detach()
        }

        // TODO: Subscribe scope.onCancel to detach
        notifyListeners(ListenerRegistration.Kind.WillAttach)

        state = State.Attached(scope)

        activeJobs.addAll(whileAttachedRunners.map { runner ->
            scope.launch {
                runner()
            }
        })

        children.forEach { it.attach(scope) }

        notifyListeners(ListenerRegistration.Kind.DidAttach)
    }

    /**
     * Detach this lifecycle instance from its current scope. If not attached, does nothing. Also detaches all of its children.
     */
    fun detach() {
        if (!isAttached) {
            return
        }

        notifyListeners(ListenerRegistration.Kind.WillDetach)

        children.forEach { it.detach() }

        state = State.Detached

        for (job in activeJobs) {
            if (job.isActive) {
                job.cancel("Lifecycle has been detached.")
            }
        }
        activeJobs.clear()


        notifyListeners(ListenerRegistration.Kind.DidDetach)
    }

    fun addChild(child: Lifecycle) {
        runIfAttached {
            child.attach(this)
        }

        children.add(child)
    }

    fun removeChild(child: Lifecycle) {
        children.remove(child)

        runIfAttached {
            child.detach()
        }
    }

    fun whileAttached(runner: suspend CoroutineScope.() -> Unit) {
        whileAttachedRunners.add(runner)

        val scope = attachedScope ?: return
        activeJobs.add(
            scope.launch {
                runner()
            }
        )
    }

    fun onWillAttach(validity: ListenerValidity = ListenerValidity.Infinite, listener: () -> Unit) {
        addListener(ListenerRegistration.Kind.WillAttach, validity, listener)
    }

    fun onDidAttach(validity: ListenerValidity = ListenerValidity.Infinite, listener: () -> Unit) {
        addListener(ListenerRegistration.Kind.DidAttach, validity, listener)
    }

    fun onWillDetach(validity: ListenerValidity = ListenerValidity.Infinite, listener: () -> Unit) {
        addListener(ListenerRegistration.Kind.WillDetach, validity, listener)
    }

    fun onDidDetach(validity: ListenerValidity = ListenerValidity.Infinite, listener: () -> Unit) {
        addListener(ListenerRegistration.Kind.DidDetach, validity, listener)
    }

    private fun addListener(kind: ListenerRegistration.Kind, validity: ListenerValidity, listener: () -> Unit) {
        listeners.getOrPut(kind, { mutableSetOf() })
            .add(ListenerRegistration(listener, validity))
    }

    private fun notifyListeners(kind: ListenerRegistration.Kind) {
        val listeners = listeners[kind] ?: return

        listeners.removeAll {
            !it.notifyAndShouldKeep()
        }
    }

    private fun runIfAttached(work: CoroutineScope.() -> Unit) {
        val attachedState = state as? State.Attached ?: return
        work(attachedState.scope)
    }

    /**
     * How long the listener is being notified. Most common is an Infinite validity where a listener is notified for the entire
     * lifetime of the Lifecycle.
     */
    sealed class ListenerValidity {
        abstract fun decrementAndShouldKeep(): Boolean

        object Infinite: ListenerValidity() {
            override fun decrementAndShouldKeep(): Boolean {
                return true
            }
        }
        object Once: ListenerValidity() {
            override fun decrementAndShouldKeep(): Boolean {
                return false
            }
        }
        class Finite(var usesLeft: Int): ListenerValidity() {
            override fun decrementAndShouldKeep(): Boolean {
                usesLeft -= 1
                return usesLeft > 0
            }
        }
    }

    private sealed class State {
        object Detached: State()
        class Attached(val scope: CoroutineScope): State()
    }

    private class ListenerRegistration(
        private val listener: () -> Unit,
        private val validity: ListenerValidity,
    ) {
        fun notifyAndShouldKeep(): Boolean {
            listener()
            return validity.decrementAndShouldKeep()
        }

        enum class Kind {
            WillAttach,
            DidAttach,
            WillDetach,
            DidDetach,
        }
    }
}