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

        init {
            ensureNeverFrozen()
            listeners.ensureNeverFrozen()
        }

        public override fun addListener(listener: ChangeTracking.Listener): CancellationToken {
            listeners.add(listener)
            return CancellationToken {
                removeListener(listener)
            }
        }

        public override fun removeListener(listener: ChangeTracking.Listener) {
            listeners.remove(listener)
        }

        public fun notifyObjectWillChange() {
            listeners.forEach { it.onObjectWillChange() }
        }

        public fun notifyObjectDidChange() {
            listeners.forEach { it.onObjectDidChange() }
        }

        override fun onObjectWillChange() {
            notifyObjectWillChange()
        }

        override fun onObjectDidChange() {
            notifyObjectDidChange()
        }
    }
}