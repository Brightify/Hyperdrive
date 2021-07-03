@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

public fun <T: Any> NonNullFlowWrapper<T>.collectWhileAttached(lifecycle: Lifecycle, collection: (T) -> Unit) {
    lifecycle.whileAttached {
        collect {
            collection(it)
        }
    }
}

/**
 * Shorthand for `Lifecycle.attach(MultiplatformGlobalScope)`.
 */
@Deprecated("Use attachToMainScope instead.", replaceWith = ReplaceWith("attachToMainScope", "org.brightify.hyperdrive.multiplatformx.attachToMainScope"))
public fun Lifecycle.attachToMultiplatformGlobalScope() {
    @Suppress("DEPRECATION")
    attach(MultiplatformGlobalScope)
}

/**
 * Shorthand for `Lifecycle.attach(MainScope())`
 */
public fun Lifecycle.attachToMainScope() {
    attach(MainScope())
}

/**
 * This class is **NOT** thread-safe. Attaching and detaching from scopes should be performed from the main thread.
 */
public class Lifecycle {
    private val children = mutableSetOf<Lifecycle>()
    private val childrenProviders = mutableSetOf<Flow<Lifecycle>>()
    private var state: State = State.Detached

    public val isAttached: Boolean
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
     * Attach this lifecycle instance to a coroutine scope.
     *
     * @throws IllegalStateException If already attached.
     */
    public fun attach(scope: CoroutineScope) {
        if (isAttached) {
            throw IllegalStateException("Trying to attach a lifecycle that's already attached to a different scope!")
        }

        // TODO: Subscribe scope.onCancel to detach
        notifyListeners(ListenerRegistration.Kind.WillAttach)

        activeJobs.addAll(whileAttachedRunners.map { runner ->
            scope.launchDetachingOnCancellation(runner)
        })

        children.forEach { it.attach(scope) }

        state = State.Attached(scope)

        notifyListeners(ListenerRegistration.Kind.DidAttach)
    }

    /**
     * Detach this lifecycle instance from its current scope. Also detaches all of its children.
     *
     * @throws IllegalStateException If not attached.
     */
    public fun detach() {
        if (!isAttached) {
            throw IllegalStateException("Trying to detach a lifecycle that is not attached!")
        }

        notifyListeners(ListenerRegistration.Kind.WillDetach)

        state = State.Detached

        children.forEach { it.detach() }

        for (job in activeJobs) {
            if (job.isActive) {
                job.cancel("Lifecycle has been detached.")
            }
        }
        activeJobs.clear()

        notifyListeners(ListenerRegistration.Kind.DidDetach)
    }

    public fun hasChild(child: Lifecycle): Boolean {
        return children.contains(child)
    }

    /**
     * Adds [child] as a dependent lifecycle sharing the same scope and attachment status as this instance.
     */
    public fun addChild(child: Lifecycle) {
        if (children.contains(child)) {
            return
        }

        runIfAttached {
            child.attach(this)
        }

        children.add(child)
    }

    public fun addChildren(childrenToAdd: Collection<Lifecycle>): Unit = childrenToAdd.forEach(::addChild)

    /**
     * Removes [child] if it's dependent of this lifecycle instance. If not does nothing.
     */
    public fun removeChild(child: Lifecycle) {
        if (!children.remove(child)) {
            return
        }

        runIfAttached {
            child.detach()
        }
    }

    public fun removeChildren(childrenToRemove: Collection<Lifecycle>): Unit  = childrenToRemove.forEach(::removeChild)

    /**
     * Launches [runner] right away if attached, does nothing otherwise.
     *
     * @return True if runner has been launched, false otherwise.
     */
    public fun runOnceIfAttached(runner: suspend CoroutineScope.() -> Unit): Boolean {
        val scope = attachedScope ?: return false
        activeJobs.add(
            scope.launchDetachingOnCancellation(runner)
        )
        return true
    }

    /**
     * Registers [runner] to be launched each time this lifecycle is attached to a scope and cancelled once detached.
     */
    public fun whileAttached(runner: suspend CoroutineScope.() -> Unit) {
        whileAttachedRunners.add(runner)

        val scope = attachedScope ?: return
        activeJobs.add(
            scope.launchDetachingOnCancellation(runner)
        )
    }

    /**
     * Registers [listener] for the [WillAttach][ListenerRegistration.Kind.WillAttach] event.
     *
     * **NOTE**: Listener is not called if this instance is already attached.
     *
     * @param validity Specify how many events should the listener receive. Defaults to an infinite validity.
     */
    public fun onWillAttach(validity: ListenerValidity = ListenerValidity.Infinite, listener: () -> Unit) {
        addListener(ListenerRegistration.Kind.WillAttach, validity, listener)
    }

    /**
     * Registers [listener] for the [DidAttach][ListenerRegistration.Kind.DidAttach] event.
     *
     * **NOTE**: Listener is not called if this instance is already attached.
     *
     * @param validity Specify how many events should the listener receive. Defaults to an infinite validity.
     */
    public fun onDidAttach(validity: ListenerValidity = ListenerValidity.Infinite, listener: () -> Unit) {
        addListener(ListenerRegistration.Kind.DidAttach, validity, listener)
    }

    /**
     * Registers [listener] for the [WillDetach][ListenerRegistration.Kind.WillDetach] event.
     *
     * **NOTE**: Listener is not called if this instance is already detached.
     *
     * @param validity Specify how many events should the listener receive. Defaults to an infinite validity.
     */
    public fun onWillDetach(validity: ListenerValidity = ListenerValidity.Infinite, listener: () -> Unit) {
        addListener(ListenerRegistration.Kind.WillDetach, validity, listener)
    }

    /**
     * Registers [listener] for the [DidDetach][ListenerRegistration.Kind.DidDetach] event.
     *
     * **NOTE**: Listener is not called if this instance is already detached.
     *
     * @param validity Specify how many events should the listener receive. Defaults to an infinite validity.
     */
    public fun onDidDetach(validity: ListenerValidity = ListenerValidity.Infinite, listener: () -> Unit) {
        addListener(ListenerRegistration.Kind.DidDetach, validity, listener)
    }

    private fun addListener(kind: ListenerRegistration.Kind, validity: ListenerValidity, listener: () -> Unit) {
        listeners.getOrPut(kind, { mutableSetOf() })
            .add(ListenerRegistration(listener, validity))
    }

    private fun CoroutineScope.launchDetachingOnCancellation(block: suspend CoroutineScope.() -> Unit) = launch {
        try {
            block()
        } catch (e: CancellationException) {
            if (isAttached) {
                detach()
            }
            throw e
        }
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
    public sealed class ListenerValidity {
        public abstract fun decrementAndShouldKeep(): Boolean

        public object Infinite: ListenerValidity() {
            override fun decrementAndShouldKeep(): Boolean {
                return true
            }
        }
        public object Once: ListenerValidity() {
            override fun decrementAndShouldKeep(): Boolean {
                return false
            }
        }
        public class Finite(private var usesLeft: Int): ListenerValidity() {
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