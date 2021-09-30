package org.brightify.hyperdrive.multiplatformx

import co.touchlab.stately.ensureNeverFrozen
import org.brightify.hyperdrive.multiplatformx.internal.ObservablePropertyProvider
import org.brightify.hyperdrive.multiplatformx.internal.PublishedPropertyProvider
import org.brightify.hyperdrive.multiplatformx.property.MutableObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.defaultEqualityPolicy
import org.brightify.hyperdrive.multiplatformx.property.impl.ConstantObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.map
import org.brightify.hyperdrive.multiplatformx.property.toKotlinMutableProperty
import org.brightify.hyperdrive.multiplatformx.property.toKotlinProperty
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

    private val properties = mutableMapOf<String, ObservableProperty<*>>()

    init {
        ensureNeverFrozen()
    }

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
     * @sample org.brightify.hyperdrive.multiplatformx.BaseViewModelSamples.notifyObjectWillChange
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
     * @sample org.brightify.hyperdrive.multiplatformx.BaseViewModelSamples.notifyObjectDidChange
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
        properties.getValue(property.name) as ObservableProperty<T>
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
        properties.getValue(property.name) as MutableObservableProperty<T>
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
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        return PublishedPropertyProvider(initialValue, equalityPolicy)
    }

    /**
     * Property delegate used for collecting changes of the provided [ObservableProperty].
     */
    @Deprecated(
        "Use `ObservableProperty.map` directly.",
        ReplaceWith("property.map(equalityPolicy, mapping)", "org.brightify.hyperdrive.multiplatformx.property.map")
    )
    protected fun <OWNER: BaseObservableObject, T, U> collected(
        property: ObservableProperty<T>,
        equalityPolicy: ObservableProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
        mapping: (T) -> U,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> = ObservablePropertyProvider {
        property.map(equalityPolicy, mapping)
    }

    protected operator fun <OWNER: BaseViewModel, T> ObservableProperty<T>.provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadOnlyProperty<OWNER, T> {
        registerViewModelProperty(property, this)
        return toKotlinProperty()
    }

    protected operator fun <OWNER: BaseViewModel, T> MutableObservableProperty<T>.provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, T> {
        registerViewModelProperty(property, this)
        return toKotlinMutableProperty()
    }

    internal fun <T> registerViewModelProperty(property: KProperty<*>, observableProperty: ObservableProperty<T>) {
        check(!properties.containsKey(property.name)) {
            "ViewModelProperty for name ${property.name} (property: $property) already registered!"
        }
        properties[property.name] = observableProperty
        observableProperty.addListener(object: ObservableProperty.Listener<T> {
            override fun valueWillChange(oldValue: T, newValue: T) {
                changeTrackingTrigger.notifyObjectWillChange()
            }

            override fun valueDidChange(oldValue: T, newValue: T) {
                changeTrackingTrigger.notifyObjectDidChange()
            }
        })
    }
}
