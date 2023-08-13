package org.brightify.hyperdrive.property

/**
 * Implemented by listeners to observe [ObservableProperty] and [DeferredObservableProperty] value changes.
 */
public interface ValueChangeListener<OLD, NEW> {
    /**
     * Listener method called before [ObservableProperty] or [DeferredObservableProperty] value changes.
     *
     * @param oldValue current value
     * @param newValue next value
     */
    public fun valueWillChange(oldValue: OLD, newValue: NEW) { }

    /**
     * Listener method called after [ObservableProperty] or [DeferredObservableProperty] value changes.
     *
     * @param oldValue previous value
     * @param newValue current value
     */
    public fun valueDidChange(oldValue: OLD, newValue: NEW) { }
}
