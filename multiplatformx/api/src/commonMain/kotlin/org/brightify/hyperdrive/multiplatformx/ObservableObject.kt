package org.brightify.hyperdrive.multiplatformx

import co.touchlab.stately.ensureNeverFrozen

/**
 * An observable object that provides a [ChangeTracking] instance.
 */
public interface ObservableObject {
    public val changeTracking: ChangeTracking

    public interface ChangeTracking {
        public fun addListener(listener: Listener): CancellationToken

        public fun addWillChangeObserver(observer: () -> Unit): CancellationToken = addListener(object: Listener {
            override fun onObjectWillChange() {
                observer()
            }
        })

        public fun addDidChangeObserver(observer: () -> Unit): CancellationToken = addListener(object: Listener {
            override fun onObjectDidChange() {
                observer()
            }
        })

        public fun removeListener(listener: Listener)

        public interface Listener {
            public fun onObjectWillChange() { }

            public fun onObjectDidChange() { }
        }
    }

    public class ChangeTrackingTrigger: ChangeTracking, ChangeTracking.Listener {
        private val listeners = mutableSetOf<ChangeTracking.Listener>()
        private val pendingListenerActions = mutableListOf<ListenerAction>()
        private var isNotifyingListeners = false

        init {
            ensureNeverFrozen()
            listeners.ensureNeverFrozen()
        }

        public override fun addListener(listener: ChangeTracking.Listener): CancellationToken {
            if (isNotifyingListeners) {
                pendingListenerActions.add(ListenerAction.Add(listener))
            } else {
                doAddListener(listener)
            }
            return CancellationToken {
                removeListener(listener)
            }
        }

        public override fun removeListener(listener: ChangeTracking.Listener) {
            if (isNotifyingListeners) {
                pendingListenerActions.add(ListenerAction.Remove(listener))
            } else {
                doRemoveListener(listener)
            }
        }

        public fun notifyObjectWillChange(): Unit = lockingListeners {
            listeners.forEach { it.onObjectWillChange() }
        }

        public fun notifyObjectDidChange(): Unit = lockingListeners {
            listeners.forEach { it.onObjectDidChange() }
        }

        override fun onObjectWillChange() {
            notifyObjectWillChange()
        }

        override fun onObjectDidChange() {
            notifyObjectDidChange()
        }

        private inline fun lockingListeners(block: () -> Unit) {
            isNotifyingListeners = true
            block()
            if (pendingListenerActions.isNotEmpty()) {
                for (action in pendingListenerActions) {
                    when (action) {
                        is ListenerAction.Add -> doAddListener(action.listener)
                        is ListenerAction.Remove -> doRemoveListener(action.listener)
                    }
                }
                pendingListenerActions.clear()
            }
            isNotifyingListeners = false
        }

        private fun doAddListener(listener: ChangeTracking.Listener) {
            listeners.add(listener)
        }

        private fun doRemoveListener(listener: ChangeTracking.Listener) {
            listeners.remove(listener)
        }

        private sealed interface ListenerAction {
            class Add(val listener: ChangeTracking.Listener): ListenerAction
            class Remove(val listener: ChangeTracking.Listener): ListenerAction
        }
    }
}