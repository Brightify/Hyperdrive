package org.brightify.hyperdrive

import org.brightify.hyperdrive.util.WeakListenerHandler

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
        private val listeners = WeakListenerHandler<ChangeTracking.Listener>(this)

        public override fun addListener(listener: ChangeTracking.Listener): CancellationToken = listeners.addListener(listener)

        public override fun removeListener(listener: ChangeTracking.Listener): Unit = listeners.removeListener(listener)

        public fun notifyObjectWillChange(): Unit = listeners.notifyListeners {
            onObjectWillChange()
        }

        public fun notifyObjectDidChange(): Unit = listeners.notifyListeners {
            onObjectDidChange()
        }

        override fun onObjectWillChange() {
            notifyObjectWillChange()
        }

        override fun onObjectDidChange() {
            notifyObjectDidChange()
        }
    }
}
