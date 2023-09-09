package org.brightify.hyperdrive

import org.brightify.hyperdrive.internal.*
import org.brightify.hyperdrive.internal.GetterSetterBoundPropertyProvider
import org.brightify.hyperdrive.internal.ObservableObjectPropertyProvider
import org.brightify.hyperdrive.internal.ObservablePropertyProvider
import org.brightify.hyperdrive.internal.PublishedPropertyProvider
import org.brightify.hyperdrive.property.MutableObservableProperty
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.property.defaultEqualityPolicy
import org.brightify.hyperdrive.property.impl.ConstantObservableProperty
import org.brightify.hyperdrive.property.map
import org.brightify.hyperdrive.property.toKotlinMutableProperty
import org.brightify.hyperdrive.property.toKotlinProperty
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

/**
 * Base for implementing observable objects. It comes with basic operators for observable properties.
 *
 * @see BaseObservableManageableObject for additional operators which require the object to be part of the lifecycle.
 */
public abstract class BaseObservableObject: ObservableObject {
    protected val changeTrackingTrigger: ObservableObject.ChangeTrackingTrigger = ObservableObject.ChangeTrackingTrigger()
    internal val internalChangeTrackingTrigger = changeTrackingTrigger
    public final override val changeTracking: ObservableObject.ChangeTracking = changeTrackingTrigger

    private val properties = mutableMapOf<String, PropertyRegistration<*>>()

    override fun toString(): String {
        val simpleName = this::class.simpleName ?: return super.toString()
        return "$simpleName@${hashCode().toUInt().toString(16)}"
    }

    /**
     * Informs the object that it's about to change/mutate.
     *
     * This has to be called before any mutation takes place. When using the provided property delegates, there should be little to no need
     * to call this method directly.
     *
     * @sample org.brightify.hyperdrive.BaseViewModelSamples.notifyObjectWillChange
     */
    protected fun notifyObjectWillChange() {
        changeTrackingTrigger.notifyObjectWillChange()
    }

    /**
     * Informs the object that it has changed/mutated.
     *
     * This has to be called after any mutation takes place. When using the provided property delegates, there should be little to no need
     * to call this method directly.
     *
     * @sample org.brightify.hyperdrive.BaseViewModelSamples.notifyObjectDidChange
     */
    protected fun notifyObjectDidChange() {
        changeTrackingTrigger.notifyObjectDidChange()
    }

    /**
     * Returns [ObservableProperty] for the given property.
     *
     * **NOTE**: Although this method can be called manually, it's not recommended due to a risk of runtime crashes. The property passed in
     * is matched by its name, so when called with a property from a different class, but a same name and different type, it will return
     * the stored [ObservableProperty] and crash later when its [value][ObservableProperty.value] is accessed or observed.
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> observe(property: KProperty0<T>): Lazy<ObservableProperty<T>> = lazy {
        properties.getValue(property.name).property as ObservableProperty<T>
    }

    /**
     * Returns [MutableObservableProperty] for the given property.
     *
     * **NOTE**: Although this method can be called manually, it's not recommended due to a risk of runtime crashes. The property passed in
     * is matched by its name, so when called with a property from a different class, but a same name and different type, it will return
     * the stored [MutableObservableProperty] and crash later when its [value][MutableObservableProperty.value] is accessed or observed.
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> observe(property: KMutableProperty0<T>): Lazy<MutableObservableProperty<T>> = lazy {
        properties.getValue(property.name).property as MutableObservableProperty<T>
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <T> observe(property: KProperty<T>): Lazy<ObservableProperty<T>> = lazy {
        properties.getValue(property.name).property as ObservableProperty<T>
    }

    /**
     * Returns a constant observable property.
     */
    protected fun <T> constant(value: T): ObservableProperty<T> {
        return ConstantObservableProperty(value)
    }

    /**
     * Property delegate used for property mutation tracking.
     *
     * Use with any property that is mutable and its mutation invalidates the view model.
     */
    protected fun <OWNER: BaseObservableObject, T> published(
        initialValue: T,
        equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
        willSet: ((T) -> Unit)? = null,
        didSet: ((T) -> Unit)? = null,
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        return PublishedPropertyProvider(initialValue, equalityPolicy, willSet, didSet)
    }

    /**
     * Property delegate used for property mutation tracking and propagating object changes from the current value when providing true
     * to the `shallow` parameter.
     */
    protected fun <OWNER: BaseObservableObject, T: ObservableObject> published(
        initialValue: T,
        shallow: Boolean = true,
        equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        return if (shallow) {
            PublishedPropertyProvider(initialValue, equalityPolicy, null, null)
        } else {
            ObservableObjectPropertyProvider(initialValue, equalityPolicy)
        }
    }

    protected fun <OWNER: BaseObservableObject, T> binding(
        property: KMutableProperty0<T>,
        equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        return GetterSetterBoundPropertyProvider(property::get, property::set, equalityPolicy)
    }

    protected fun <OWNER: BaseObservableObject, T, U> binding(
        property: KMutableProperty0<U>,
        equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
        fromProperty: (U) -> T,
        toProperty: (T) -> U,
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        return GetterSetterBoundPropertyProvider({ fromProperty(property.get()) }, { property.set(toProperty(it)) }, equalityPolicy)
    }

    protected fun <OWNER: BaseObservableObject, T> binding(
        property: MutableObservableProperty<T>,
        equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
        localWillChange: (T) -> Unit = { },
        localDidChange: (T) -> Unit = { },
        boundWillChange: (T) -> Unit = { },
        boundDidChange: (T) -> Unit = { },
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        return MutableObservablePropertyBoundPropertyProvider(
            property,
            equalityPolicy,
            localWillChange,
            localDidChange,
            boundWillChange,
            boundDidChange,
        )
    }

    protected fun <OWNER: BaseObservableObject, T, P: ObservableProperty<T>, U> tracking(
        property: P,
        equalityPolicy: ObservableProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
        read: P.(T) -> U,
        write: P.(U) -> Unit,
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, U>> {
        return TrackingPropertyProvider(property, equalityPolicy, read, write)
    }

    /**
     * Property delegate used for collecting changes of the provided [ObservableProperty].
     */
    @Deprecated(
        "Use `ObservableProperty.map` directly.",
        ReplaceWith("property.map(equalityPolicy, mapping)", "org.brightify.hyperdrive.property.map")
    )
    protected fun <OWNER: BaseObservableObject, T, U> collected(
        property: ObservableProperty<T>,
        equalityPolicy: ObservableProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
        mapping: (T) -> U,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> = ObservablePropertyProvider {
        property.map(equalityPolicy, mapping)
    }

    protected operator fun <OWNER: ObservableObject, T> ObservableProperty<T>.provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadOnlyProperty<OWNER, T> {
        trackObservablePropertyChanges(property, this)
        return toKotlinProperty()
    }

    protected operator fun <OWNER: ObservableObject, T> MutableObservableProperty<T>.provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, T> {
        trackObservablePropertyChanges(property, this)
        return toKotlinMutableProperty()
    }

    protected fun <T> trackObservablePropertyChanges(property: KProperty<*>, observableProperty: ObservableProperty<T>) {
        check(!properties.containsKey(property.name)) {
            "ViewModelProperty for name ${property.name} (property: $property) already registered!"
        }
        val listenerRegistration = observableProperty.addListener(object: ObservableProperty.Listener<T> {
            override fun valueWillChange(oldValue: T, newValue: T) {
                changeTrackingTrigger.notifyObjectWillChange()
            }

            override fun valueDidChange(oldValue: T, newValue: T) {
                changeTrackingTrigger.notifyObjectDidChange()
            }
        })
        properties[property.name] = PropertyRegistration(
            property = observableProperty,
            listenerRegistration = listenerRegistration,
        )
    }

    internal fun <T> _internal_trackObservablePropertyChanges(property: KProperty<*>, observableProperty: ObservableProperty<T>) {
        trackObservablePropertyChanges(property, observableProperty)
    }

    private class PropertyRegistration<T>(val property: ObservableProperty<T>, val listenerRegistration: CancellationToken)
}
