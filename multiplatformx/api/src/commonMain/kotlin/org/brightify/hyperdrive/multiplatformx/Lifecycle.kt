@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package org.brightify.hyperdrive.multiplatformx

import co.touchlab.stately.ensureNeverFrozen
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.plus
import org.brightify.hyperdrive.multiplatformx.util.bridge.NonNullFlowWrapper
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

public fun <T: Any> NonNullFlowWrapper<T>.collectWhileAttached(lifecycle: Lifecycle, collection: (T) -> Unit) {
    lifecycle.whileAttached {
        collect {
            collection(it)
        }
    }
}

@Suppress("DEPRECATION")
@Deprecated("Use attachToMainScope instead.", replaceWith = ReplaceWith("attachToMainScope", "org.brightify.hyperdrive.multiplatformx.attachToMainScope"))
public fun LifecycleGraph.Root.attachToMultiplatformGlobalScope(): CancellationToken
    = attach(MultiplatformGlobalScope)

public fun LifecycleGraph.Root.attachToMainScope(): CancellationToken = attach(MainScope())

public fun LifecycleGraph.Root.attachToMainScope(unhandledExceptionHandler: (CoroutineContext, Throwable) -> Unit): CancellationToken {
    return attach(MainScope() + CoroutineExceptionHandler(unhandledExceptionHandler))
}

private typealias RunnerId = ULong

public fun LifecycleRoot(owner: Any? = null): LifecycleGraph.Root {
    return LifecycleGraph.Root(owner)
}

/**
 * This class is **NOT** thread-safe. Attaching and detaching from scopes should be performed from the main thread.
 */
public sealed class LifecycleGraph {
    public val isAttached: Boolean
        get() = state is State.Attached

    public val debugDescription: String
        get() {
            val selfDescription = "Lifecycle@${hashCode().toUInt().toString(16)}"
            return owner?.let {
                "$it ($selfDescription)"
            } ?: selfDescription
        }

    protected abstract val owner: Any?
    internal var state: State = State.Detached
        set(newState) {
            if (newState == field) { return }
            val isAttaching = field is State.Detached && newState is State.Attached
            val isDetaching = field is State.Attached && newState is State.Detached
            check(isAttaching || isDetaching) { "Invalid state change! Transition from $field to $newState is not supported!" }

            if (isAttaching) {
                notifyListeners(ListenerRegistration.Kind.WillAttach)
            } else if (isDetaching) {
                notifyListeners(ListenerRegistration.Kind.WillDetach)
            }

            when (newState) {
                is State.Attached -> {
                    activeJobs.putAll(whileAttachedRunners.mapValues { (_, runner) -> newState.scope.launch(block = runner) })
                }
                is State.Detached -> {
                    activeJobs.values.forEach { job ->
                        if (job.isActive) {
                            job.cancel("Lifecycle has been detached.")
                        }
                    }
                    activeJobs.clear()
                }
            }
            children.forEach { it.state = newState }
            field = newState

            if (isAttaching) {
                notifyListeners(ListenerRegistration.Kind.DidAttach)
            } else if (isDetaching) {
                notifyListeners(ListenerRegistration.Kind.DidDetach)
            }
        }

    private var runnerIdSequence: RunnerId = 0u
    private val whileAttachedRunners = mutableMapOf<RunnerId, suspend CoroutineScope.() -> Unit>()

    private val children = mutableSetOf<Node>()
    private val listeners = mutableSetOf<LifecycleListener>()
    private val eventListeners = mutableMapOf<ListenerRegistration.Kind, MutableSet<ListenerRegistration>>()

    private val attachedScope: CoroutineScope?
        get() = when(val state = state) {
            State.Detached -> null
            is State.Attached -> state.scope
        }

    private var activeJobs = mutableMapOf<RunnerId, Job>()

    init {
        ensureNeverFrozen()
    }

    public fun topMostNode(): LifecycleGraph {
        return when (this) {
            is Node -> root?.topMostNode() ?: parent?.topMostNode() ?: this
            is Root -> this
        }
    }

    /**
     * Adds [child] as a dependent lifecycle sharing the same scope and attachment status as this instance.
     */
    public fun addChild(child: Node) {
        require(child.parent == null) {
            "$debugDescription: Could not add child which already has a parent:\n${child.dumpBranchToRootLines().joinToString("\n") { "    $it" } }"
        }
        children.add(child)
        child.parent = this
        child.root = when (this) {
            is Root -> this
            is Node -> root
        }
        child.state = state
    }

    public fun addChildren(childrenToAdd: Collection<Node>): Unit = childrenToAdd.forEach(::addChild)

    public fun removeChild(child: Node) {
        require(child.parent === this) {
            "$debugDescription: Could not remove child of a different parent:\n${child.dumpBranchToRootLines().joinToString("\n") { "    $it" } }"
        }

        child.state = State.Detached
        child.parent = null
        child.root = null
        children.remove(child)
    }

    public fun removeChildren(childrenToRemove: Collection<Lifecycle>): Unit  = childrenToRemove.forEach(::removeChild)

    public fun hasChild(child: Node): Boolean {
        return children.contains(child)
    }

    protected fun runIfAttached(work: CoroutineScope.() -> Unit) {
        val attachedState = state as? State.Attached ?: return
        work(attachedState.scope)
    }

    /**
     * Launches [runner] right away if attached, does nothing otherwise.
     *
     * @return True if runner has been launched, false otherwise.
     */
    public fun runOnceIfAttached(runner: suspend CoroutineScope.() -> Unit): Boolean {
        val scope = attachedScope ?: return false
        activeJobs[nextRunnerId()] = scope.launch(block = runner)
        return true
    }

    /**
     * Registers [runner] to be launched each time this lifecycle is attached to a scope and cancelled once detached.
     */
    public fun whileAttached(runner: suspend CoroutineScope.() -> Unit): CancellationToken {
        val id = nextRunnerId()
        val cancellation = CancellationToken {
            whileAttachedRunners.remove(id)
            activeJobs.remove(id)?.cancel("whileAttached() registration has been cancelled.")
        }

        whileAttachedRunners[id] = runner

        val scope = attachedScope ?: return cancellation
        activeJobs[id] = scope.launch(block = runner)
        return cancellation
    }

    private fun nextRunnerId(): RunnerId = runnerIdSequence++

    /**
     * Registers [listener] for the [WillAttach][ListenerRegistration.Kind.WillAttach] event.
     *
     * **NOTE**: Listener is not called if this instance is already attached.
     *
     * @param validity Specify how many events should the listener receive. Defaults to an infinite validity.
     */
    public fun onWillAttach(validity: LifecycleListener.Validity = LifecycleListener.Validity.Infinite, listener: () -> Unit): CancellationToken
        = addListener(ListenerRegistration.Kind.WillAttach, validity, listener)

    /**
     * Registers [listener] for the [DidAttach][ListenerRegistration.Kind.DidAttach] event.
     *
     * **NOTE**: Listener is not called if this instance is already attached.
     *
     * @param validity Specify how many events should the listener receive. Defaults to an infinite validity.
     */
    public fun onDidAttach(validity: LifecycleListener.Validity = LifecycleListener.Validity.Infinite, listener: () -> Unit): CancellationToken
        = addListener(ListenerRegistration.Kind.DidAttach, validity, listener)

    /**
     * Registers [listener] for the [WillDetach][ListenerRegistration.Kind.WillDetach] event.
     *
     * **NOTE**: Listener is not called if this instance is already detached.
     *
     * @param validity Specify how many events should the listener receive. Defaults to an infinite validity.
     */
    public fun onWillDetach(validity: LifecycleListener.Validity = LifecycleListener.Validity.Infinite, listener: () -> Unit): CancellationToken
        = addListener(ListenerRegistration.Kind.WillDetach, validity, listener)

    /**
     * Registers [listener] for the [DidDetach][ListenerRegistration.Kind.DidDetach] event.
     *
     * **NOTE**: Listener is not called if this instance is already detached.
     *
     * @param validity Specify how many events should the listener receive. Defaults to an infinite validity.
     */
    public fun onDidDetach(validity: LifecycleListener.Validity = LifecycleListener.Validity.Infinite, listener: () -> Unit): CancellationToken
        = addListener(ListenerRegistration.Kind.DidDetach, validity, listener)

    public fun addListener(listener: LifecycleListener): CancellationToken {
        listeners.add(listener)
        return CancellationToken {
            removeListener(listener)
        }
    }

    public fun removeListener(listener: LifecycleListener) {
        listeners.remove(listener)
    }

    internal fun addListener(kind: ListenerRegistration.Kind, validity: LifecycleListener.Validity, listener: () -> Unit): CancellationToken {
        val registration = ListenerRegistration(listener, validity)
        eventListeners.getOrPut(kind) { mutableSetOf() }.add(registration)
        return CancellationToken {
            eventListeners[kind]?.remove(registration)
        }
    }

    private fun notifyListeners(kind: ListenerRegistration.Kind) {
        listeners.forEach {
            when (kind) {
                ListenerRegistration.Kind.WillAttach -> it.willAttach()
                ListenerRegistration.Kind.DidAttach -> it.didAttach()
                ListenerRegistration.Kind.WillDetach -> it.willDetach()
                ListenerRegistration.Kind.DidDetach -> it.didDetach()
            }
        }

        val listeners = eventListeners[kind] ?: return

        listeners.removeAll {
            !it.notifyAndShouldKeep()
        }
    }

    internal sealed class State {
        object Detached: State()
        class Attached(val scope: CoroutineScope): State()
    }

    internal class ListenerRegistration(
        private val listener: () -> Unit,
        private val validity: LifecycleListener.Validity,
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

    public fun dumpTree(): String = dumpTreeLines().joinToString("\n")

    protected fun dumpTreeLines(): List<String> {
        val lastChildIndex = children.count() - 1
        return listOf(debugDescription) + children.flatMapIndexed { childIndex, child ->
            val childTreeLines = child.dumpTreeLines()
            childTreeLines.mapIndexed { lineIndex, line ->
                if (lineIndex == 0) {
                    if (childIndex < lastChildIndex) {
                        "├───"
                    } else {
                        "└───"
                    }
                } else {
                    if (childIndex < lastChildIndex) {
                        "│   "
                    } else {
                        "    "
                    }
                } + " $line"
            }
        }
    }

    public class Root(override val owner: Any?): LifecycleGraph() {
        /**
         * Attach this lifecycle instance to a coroutine scope the method has been called in.
         *
         * @throws IllegalStateException If already attached.
         */
        public fun attach(scope: CoroutineScope): CancellationToken {
            check(state == State.Detached) { "Couldn't attach lifecycle root to $this. Already attached: $state." }
            state = State.Attached(scope)
            val job = scope.launch {
                try {
                    awaitCancellation()
                } finally {
                    state = State.Detached
                }
            }
            return CancellationToken {
                job.cancel()
            }
        }
    }

    public class Node(override val owner: Any): LifecycleGraph() {
        internal var parent: LifecycleGraph? = null
        internal var root: Root? = null

        public fun removeFromParent() {
            parent?.removeChild(this)
        }

        public fun dumpBranchToRoot(): String = dumpBranchToRootLines().joinToString("\n")

        internal fun dumpBranchToRootLines(): List<String> {
            return listOf(debugDescription) + parent.let { parent ->
                val parentBranchLines = when (parent) {
                    is Node -> parent.dumpBranchToRootLines()
                    is Root -> listOf(parent.debugDescription)
                    null -> listOf("No root parent")
                }
                parentBranchLines.mapIndexed { lineIndex, line ->
                    if (lineIndex == 0) {
                        "└───"
                    } else {
                        "    "
                    } + " $line"
                }
            }
        }

        override fun toString(): String {
            return debugDescription
        }
    }

}

public interface LifecycleListener {
    public fun willAttach() { }
    public fun didAttach() { }
    public fun willDetach() { }
    public fun didDetach() { }

    /**
     * How long the listener is being notified. Most common is an Infinite validity where a listener is notified for the entire
     * lifetime of the Lifecycle.
     */
    public sealed class Validity {
        public abstract fun decrementAndShouldKeep(): Boolean

        public object Infinite: Validity() {
            override fun decrementAndShouldKeep(): Boolean {
                return true
            }
        }
        public object Once: Validity() {
            override fun decrementAndShouldKeep(): Boolean {
                return false
            }
        }
        public class Finite(private var usesLeft: Int): Validity() {
            override fun decrementAndShouldKeep(): Boolean {
                usesLeft -= 1
                return usesLeft > 0
            }
        }
    }
}

public typealias Lifecycle = LifecycleGraph.Node
