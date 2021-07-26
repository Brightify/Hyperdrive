package org.brightify.hyperdrive.multiplatformx.property.impl

import org.brightify.hyperdrive.multiplatformx.CancellationToken
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty

internal class SwitchMappingViewModelProperty<T, U>(
    private val switchMapped: ViewModelProperty<T>,
    private val transform: (T) -> ViewModelProperty<U>,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<ViewModelProperty<U>>,
): ViewModelProperty<U>, ViewModelProperty.ValueChangeListener<T> {
    private var activeBacking: ViewModelProperty<U> = transform(switchMapped.value)
    private var pendingActiveBacking: ViewModelProperty<U>? = null

    override val value: U
        get() = activeBacking.value

    private val listeners = ViewModelPropertyListeners(this)
    private val passthroughListener = PassthroughValueChangeListener()

    init {
        switchMapped.addListener(this)
    }

    override fun valueWillChange(newValue: T) {
        val newActiveBacking = transform(newValue)
        if (equalityPolicy.isEqual(activeBacking, newActiveBacking)) { return }
        pendingActiveBacking = newActiveBacking
        listeners.notifyValueWillChange(newActiveBacking.value)
    }

    override fun valueDidChange(oldValue: T) {
        val oldActiveBacking = activeBacking
        activeBacking = pendingActiveBacking ?: return
        pendingActiveBacking = null

        // Only remove the listener if we replaced the active backing.
        oldActiveBacking.removeListener(passthroughListener)
        listeners.notifyValueDidChange(oldActiveBacking.value)
        activeBacking.addListener(passthroughListener)
    }

    override fun addListener(listener: ViewModelProperty.ValueChangeListener<U>): CancellationToken = listeners.addListener(listener)

    override fun removeListener(listener: ViewModelProperty.ValueChangeListener<U>): Boolean = listeners.removeListener(listener)

    inner class PassthroughValueChangeListener: ViewModelProperty.ValueChangeListener<U> {
        override fun valueWillChange(newValue: U) {
            listeners.notifyValueWillChange(newValue)
        }

        override fun valueDidChange(oldValue: U) {
            listeners.notifyValueDidChange(oldValue)
        }
    }
}