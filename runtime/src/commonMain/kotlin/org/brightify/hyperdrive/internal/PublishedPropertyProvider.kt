package org.brightify.hyperdrive.internal

import org.brightify.hyperdrive.BaseObservableObject
import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.property.MutableObservableProperty
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.property.impl.ValueChangeListenerHandler
import org.brightify.hyperdrive.property.impl.ValueObservableProperty

internal class PublishedPropertyProvider<OWNER: BaseObservableObject, T>(
    initialValue: T,
    equalityPolicy: ObservableProperty.EqualityPolicy<T>,
    willSet: ((T) -> Unit)?,
    didSet: ((T) -> Unit)?,
): MutableObservablePropertyProvider<OWNER, T>(
    observablePropertyFactory = {
        PublishedValueObservableProperty(initialValue, equalityPolicy, willSet, didSet)
    }
)

internal class PublishedValueObservableProperty<T>(
    initialValue: T,
    private val equalityPolicy: ObservableProperty.EqualityPolicy<T>,
    private val willSet: ((T) -> Unit)?,
    private val didSet: ((T) -> Unit)?,
): MutableObservableProperty<T> {
    override var value: T = initialValue
        set(newValue) {
            if (equalityPolicy.isEqual(field, newValue)) { return }
            willSet?.invoke(newValue)
            listeners.runNotifyingListeners(newValue) {
                field = it
            }
            didSet?.invoke(newValue)
        }

    private val listeners = ValueChangeListenerHandler(this)

    override fun addListener(listener: ObservableProperty.Listener<T>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ObservableProperty.Listener<T>) = listeners.removeListener(listener)
}
