@file:Suppress("MemberVisibilityCanBePrivate")

package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.flow.*
import org.brightify.hyperdrive.multiplatformx.internal.BoundPropertyProvider
import org.brightify.hyperdrive.multiplatformx.internal.CollectedPropertyProvider
import org.brightify.hyperdrive.multiplatformx.internal.ManagedPropertyProvider
import org.brightify.hyperdrive.multiplatformx.internal.PublishedPropertyProvider
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import co.touchlab.stately.ensureNeverFrozen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.brightify.hyperdrive.multiplatformx.internal.ManagedListPropertyProvider
import org.brightify.hyperdrive.multiplatformx.internal.MutableManagedListPropertyProvider
import org.brightify.hyperdrive.multiplatformx.internal.MutableManagedPropertyProvider
import org.brightify.hyperdrive.multiplatformx.internal.ViewModelPropertyProvider
import org.brightify.hyperdrive.multiplatformx.property.MutableViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.defaultEqualityPolicy
import org.brightify.hyperdrive.multiplatformx.property.impl.CollectedViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.impl.ValueViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.map
import org.brightify.hyperdrive.multiplatformx.property.flatMapLatest
import org.brightify.hyperdrive.multiplatformx.property.toKotlinMutableProperty
import org.brightify.hyperdrive.multiplatformx.property.toKotlinProperty

/**
 * Common behavior for all view models.
 *
 * This class contains a few of property delegates to hide unnecessary instrumentation code from view models. As a single point of interaction
 * with the native code for both Android and iOS (and possible other platforms), view models have to provide an easy way for the native code
 * to consume them. Thanks to the behavior described below, developers of the view models can work directly with values without asynchronicity.
 *
 * ## Support for SwiftUI
 *
 * Using the provided property delegates (or calling [notifyObjectWillChange] before a mutation) sends a notification to the [observeObjectWillChange]
 * ands its wrapper [observeObjectWillChangeWrapper]. In the native code that depends on a module using [BaseViewModel], add the following implementation:
 *
 * ```
 * #warning("Don't forget to import the Kotlin multiplatform framework instead of this warning.")
 * import Combine
 *
 * extension BaseViewModel: ObservableObject {
 *     private static var objectWillChangeKey: UInt8 = 0
 *     public var objectWillChange: ObservableObjectPublisher {
 *         if let publisher = objc_getAssociatedObject(self, &Self.objectWillChangeKey) as? ObservableObjectPublisher {
 *             return publisher
 *         }
 *         let publisher = ObjectWillChangePublisher()
 *         objc_setAssociatedObject(self, &Self.objectWillChangeKey, publisher, objc_AssociationPolicy.OBJC_ASSOCIATION_RETAIN)
 *         observeObjectWillChangeWrapper.collectWhileAttached(lifecycle: lifecycle) { _ in
 *             publisher.send()
 *         }
 *         return publisher
 *     }
 * }
 *
 * extension BaseViewModel: Identifiable { }
 * ```
 *
 * ## Support for Jetpack Compose
 *
 * Jetpack Compose has support for using [StateFlow] to drive the views by converting it to state (as seen in an example below).
 * To help with this, all properties that use the [published] or [collected] delegates can be used in the [observe] method which returns
 * a [StateFlow] instance which publishes a new value on each change to the property. To further minimize required
 * boilerplate, the Hyperdrive Kotlin compiler plugin generates `observeX` properties for each property `x` serving as a quick access to the
 * [StateFlow] for the given property.
 *
 * ```
 * @ViewModel
 * class SampleViewModel: BaseViewModel() {
 *     var message: String by published("Default message")
 *         private set
 * }
 *
 * @Composable
 * fun SampleView(viewModel: SampleViewModel) {
 *     val message by viewModel.observeMessage.asState()
 *     Text(message)
 * }
 * ```
 *
 * **NOTE**: Annotated your class with [ViewModel] and make it inherit [BaseViewModel] to mark the class for processing by the Hyperdrive Kotlin compiler plugin.
 *
 * ## Future plans:
 * - Maybe replace delegates with annotations and make the plugin replace the code during compilation. This would probably result in a better
 *   readability, but more magic behind the scenes.
 * - Add `ObservableObject` interface and make the [published] delegate track changes inside the object similarly how [managed] does it with
 *   child view models.
 * - Include required Swift code as a template in resources allowing for an easy import.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public abstract class BaseViewModel: ManageableViewModel {
    private val properties = mutableMapOf<String, ViewModelProperty<*>>()

    internal val changeTrackingTrigger = ManageableViewModel.ChangeTrackingTrigger()
    public final override val changeTracking: ManageableViewModel.ChangeTracking = changeTrackingTrigger

    public final override val lifecycle: Lifecycle = Lifecycle(this)

    private val locks = LockRegistry()

    init {
        ensureNeverFrozen()
        lifecycle.ensureNeverFrozen()

        lifecycle.whileAttached {
            whileAttached()
        }
    }

    override fun toString(): String {
        val simpleName = this::class.simpleName ?: return super.toString()
        return "$simpleName@${hashCode().toUInt().toString(16)}"
    }

    /**
     * Returns [StateFlow] for the given property.
     *
     * **NOTE**: Although this method can be called manually, it's not recommended due to a risk of runtime crashes. The property passed in
     * is matched by its name, so when called with a property from a different class, but a same name and different type, it will return
     * the stored [StateFlow] and crash later when its [value][StateFlow.value] is accessed or the flow is collected.
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> observe(property: KProperty0<T>): Lazy<ViewModelProperty<T>> = lazy {
        properties.getValue(property.name) as ViewModelProperty<T>
    }

    /**
     * Override this method to perform any long-running cancellable async work.
     *
     * Calling super is not required, it does nothing. Overriding this method is a shorthand for:
     *
     * ```
     * init {
     *     lifecycle.whileAttached {
     *         // Do work here.
     *     }
     * }
     * ```
     *
     * @see Lifecycle.whileAttached
     */
    protected open suspend fun whileAttached() { }

    /**
     * Property delegate used for property mutation tracking.
     *
     * Use with any property that is mutable and its mutation invalidates the view model.
     */
    protected fun <OWNER: BaseViewModel, T> published(
        initialValue: T,
        equalityPolicy: ViewModelProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        return PublishedPropertyProvider(initialValue, equalityPolicy)
    }

    /**
     * Property delegate used to mirror an instance of [StateFlow].
     *
     * The property using this delegate will keep its value synchronized with the [StateFlow] as long as the [Lifecycle] of this view model
     * is attached. Although [StateFlow] always has a value that can be accessed in synchronous code, this delegate only uses the value
     * as an initial value. This means that when detached, the property is not kept in sync with the source [StateFlow] instance.
     */
    protected fun <OWNER: BaseViewModel, T> collected(
        stateFlow: StateFlow<T>,
        equalityPolicy: ViewModelProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, T>> {
        return withNonRepeatingStateFlow(stateFlow) { initialValue, autoFilteredFlow ->
            CollectedPropertyProvider(initialValue, autoFilteredFlow, equalityPolicy)
        }
    }

    /**
     * Property delegate used to mirror an instance of [StateFlow], mapping its value.
     *
     * @see collected
     */
    protected fun <OWNER: BaseViewModel, T, U> collected(
        stateFlow: StateFlow<T>,
        equalityPolicy: ViewModelProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
        mapping: (T) -> U,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> {
        return withNonRepeatingStateFlow(stateFlow) { initialValue, autoFilteredFlow ->
            CollectedPropertyProvider(mapping(initialValue), autoFilteredFlow.map { mapping(it) }, equalityPolicy)
        }
    }

    protected fun <OWNER: BaseViewModel, T, U> collected(
        property: ViewModelProperty<T>,
        equalityPolicy: ViewModelProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
        mapping: (T) -> U,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> = ViewModelPropertyProvider {
        property.map(equalityPolicy, mapping)
    }

    /**
     * Property delegate used to mirror the latest value of a [Flow].
     *
     * As opposed to the [collected] method for [StateFlow], this one requires passing in an [initialValue] so that this property has a value
     * even before the view model's [lifecycle] gets attached.
     *
     * @see collected
     */
    protected fun <OWNER: BaseViewModel, T> collected(
        initialValue: T,
        flow: Flow<T>,
        equalityPolicy: ViewModelProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, T>> {
        return CollectedPropertyProvider(initialValue, flow, equalityPolicy)
    }

    /**
     * Property delegate used to mirror the latest value of a [Flow], mapping its value.
     *
     * Using this delegate is a shorthand for using the one without mapping and mapping the value using the [Flow.map] operator, with one notable difference.
     * In both cases the type of the initial value is required to be the same as the type in the [Flow]. So using this method, the mapping
     * is applied to the initial value as well. Using the method without mapping does not require mapping it.
     *
     * **TODO**: We might want to add an additional method that will accept mapping, but the initial value type will the same as the output of the mapping.
     *
     * @see collected
     */
    protected fun <OWNER: BaseViewModel, T, U> collected(
        initialValue: T,
        flow: Flow<T>,
        equalityPolicy: ViewModelProperty.EqualityPolicy<U>,
        mapping: (T) -> U,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> {
        return CollectedPropertyProvider(mapping(initialValue), flow.map { mapping(it) }, equalityPolicy)
    }

    @Deprecated(message = "This behaves as FlatMapLatest, do not use.", replaceWith = ReplaceWith("collectedFlatMapLatest"))
    protected fun <OWNER: BaseViewModel, T, U> collectedFlatMap(
        stateFlow: StateFlow<T>,
        equalityPolicy: ViewModelProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
        flatMapping: (T) -> StateFlow<U>,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> = collectedFlatMapLatest(stateFlow, equalityPolicy, flatMapping)

    /**
     * Property delegate used to mirror an instance of [StateFlow], flat-mapping its value and only observing the latest's StateFlow's changes.
     *
     * @see collected
     */
    protected fun <OWNER: BaseViewModel, T, U> collectedFlatMapLatest(
        stateFlow: StateFlow<T>,
        equalityPolicy: ViewModelProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
        flatMapping: (T) -> StateFlow<U>,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> {
        return CollectedPropertyProvider(
            flatMapping(stateFlow.value).value,
            stateFlow.withIndex().flatMapLatest { (index, value) ->
                if (index == 0) {
                    // FIXME: This might be dropping a value we don't want to be dropped.
                    flatMapping(value).drop(1)
                } else {
                    flatMapping(value)
                }
            },
            equalityPolicy,
        )
    }

    @Deprecated(message = "This behaves as FlatMapLatest, do not use.", replaceWith = ReplaceWith("collectedFlatMapLatest"))
    protected fun <OWNER: BaseViewModel, T, U> collectedFlatMap(
        property: ViewModelProperty<T>,
        equalityPolicy: ViewModelProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
        flatMapping: (T) -> StateFlow<U>,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> = collectedFlatMapLatest(property, equalityPolicy, flatMapping)

    protected fun <OWNER: BaseViewModel, T, U> collectedFlatMapLatest(
        property: ViewModelProperty<T>,
        equalityPolicy: ViewModelProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
        flatMapping: (T) -> StateFlow<U>,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> {
        return ViewModelPropertyProvider { owner ->
            property.flatMapLatest { value ->
                withNonRepeatingStateFlow(flatMapping(value)) { initialValue, autoFilteredFlow ->
                    CollectedViewModelProperty(autoFilteredFlow, owner.lifecycle, equalityPolicy, initialValue)
                }
            }
        }
    }

    /**
     * Property delegate used for view model composition.
     *
     * Any child view model that is a part of your view model should be initiated using the managed delegate. Its [lifecycle] then automatically
     * attached and detached from the parent view model's [lifecycle].
     *
     * @sample org.brightify.hyperdrive.multiplatformx.BaseViewModelSamples.managed
     */
    protected fun <OWNER: BaseViewModel, VM: ManageableViewModel?> managed(
        childModel: VM,
        published: Boolean = false,
        equalityPolicy: ViewModelProperty.EqualityPolicy<VM> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, VM>> = MutableManagedPropertyProvider(published) {
        ValueViewModelProperty(childModel, equalityPolicy)
    }

    protected fun <OWNER: BaseViewModel, VM: ManageableViewModel?> managed(
        childStateFlow: StateFlow<VM>,
        published: Boolean = false,
        equalityPolicy: ViewModelProperty.EqualityPolicy<VM> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, VM>> {
        return withNonRepeatingStateFlow(childStateFlow) { initialValue, autoFilteredFlow ->
            ManagedPropertyProvider(published) { owner ->
                CollectedViewModelProperty(autoFilteredFlow, owner.lifecycle, equalityPolicy, initialValue)
            }
        }
    }

    protected fun <OWNER: BaseViewModel, T, VM: ManageableViewModel?> managed(
        valueStateFlow: StateFlow<T>,
        published: Boolean = false,
        equalityPolicy: ViewModelProperty.EqualityPolicy<VM> = defaultEqualityPolicy(),
        mapping: (T) -> VM,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, VM>> {
        return withNonRepeatingStateFlow(valueStateFlow) { initialValue, autoFilteredFlow ->
            ManagedPropertyProvider(published) { owner ->
                CollectedViewModelProperty(autoFilteredFlow.map { mapping(it) }, owner.lifecycle, equalityPolicy, mapping(initialValue))
            }
        }
    }

    protected fun <OWNER: BaseViewModel, T, VM: ManageableViewModel> managed(
        property: ViewModelProperty<T>,
        published: Boolean,
        equalityPolicy: ViewModelProperty.EqualityPolicy<VM> = defaultEqualityPolicy(),
        mapping: (T) -> VM,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, VM>> {
        return ManagedPropertyProvider(published) {
            property.map(equalityPolicy, mapping)
        }
    }

    protected fun <OWNER: BaseViewModel, T, VM: ManageableViewModel?> managed(
        initialChild: VM,
        valueFlow: Flow<T>,
        published: Boolean = false,
        equalityPolicy: ViewModelProperty.EqualityPolicy<VM> = defaultEqualityPolicy(),
        mapping: suspend (T) -> VM,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, VM>> {
        return ManagedPropertyProvider(published) { owner ->
            CollectedViewModelProperty(valueFlow.map { mapping(it) }, owner.lifecycle, equalityPolicy, initialChild)
        }
    }

    protected fun <OWNER: BaseViewModel, VM: ManageableViewModel?> managed(
        initialChild: VM,
        childFlow: Flow<VM>,
        published: Boolean = false,
        equalityPolicy: ViewModelProperty.EqualityPolicy<VM> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, VM>> {
        return ManagedPropertyProvider(published) { owner ->
            CollectedViewModelProperty(childFlow, owner.lifecycle, equalityPolicy, initialChild)
        }
    }

    protected fun <OWNER: BaseViewModel, VM: ManageableViewModel> managedList(
        childModels: List<VM>,
        published: Boolean = false,
        equalityPolicy: ViewModelProperty.EqualityPolicy<List<VM>> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, List<VM>>> {
        return MutableManagedListPropertyProvider(published) {
            ValueViewModelProperty(childModels, equalityPolicy)
        }
    }

    protected fun <OWNER: BaseViewModel, VM: ManageableViewModel?> managedList(
        childStateFlow: StateFlow<List<VM>>,
        published: Boolean = false,
        equalityPolicy: ViewModelProperty.EqualityPolicy<List<VM>> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, List<VM>>> {
        return withNonRepeatingStateFlow(childStateFlow) { initialValue, autoFilteredFlow ->
            ManagedListPropertyProvider(published) { owner ->
                CollectedViewModelProperty(autoFilteredFlow, owner.lifecycle, equalityPolicy, initialValue)
            }
        }
    }

    protected fun <OWNER: BaseViewModel, T, VM: ManageableViewModel?> managedList(
        valueStateFlow: StateFlow<T>,
        published: Boolean = false,
        equalityPolicy: ViewModelProperty.EqualityPolicy<List<VM>> = defaultEqualityPolicy(),
        mapping: (T) -> List<VM>,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, List<VM>>> {
        return withNonRepeatingStateFlow(valueStateFlow) { initialValue, autoFilteredFlow ->
            ManagedListPropertyProvider(published) { owner ->
                CollectedViewModelProperty(autoFilteredFlow.map { mapping(it) }, owner.lifecycle, equalityPolicy, mapping(initialValue))
            }
        }
    }

    protected fun <OWNER: BaseViewModel, T, VM: ManageableViewModel?> managedList(
        property: ViewModelProperty<List<VM>>,
        published: Boolean = false,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, List<VM>>> {
        return ManagedListPropertyProvider(published) { owner ->
            property
        }
    }

    protected fun <OWNER: BaseViewModel, T, VM: ManageableViewModel?> managedList(
        initialChild: List<VM>,
        valueFlow: Flow<T>,
        published: Boolean = false,
        equalityPolicy: ViewModelProperty.EqualityPolicy<List<VM>> = defaultEqualityPolicy(),
        mapping: suspend (T) -> List<VM>,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, List<VM>>> {
        return ManagedListPropertyProvider(published) { owner ->
            CollectedViewModelProperty(valueFlow.map { mapping(it) }, owner.lifecycle, equalityPolicy, initialChild)
        }
    }

    protected fun <OWNER: BaseViewModel, VM: ManageableViewModel?> managedList(
        initialChild: List<VM>,
        childFlow: Flow<List<VM>>,
        published: Boolean = false,
        equalityPolicy: ViewModelProperty.EqualityPolicy<List<VM>> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, List<VM>>> {
        return ManagedListPropertyProvider(published) { owner ->
            CollectedViewModelProperty(childFlow, owner.lifecycle, equalityPolicy, initialChild)
        }
    }

    protected fun <OWNER: BaseViewModel, T> binding(
        stateFlow: MutableStateFlow<T>,
        equalityPolicy: ViewModelProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        return BoundPropertyProvider(stateFlow.value, stateFlow, { it }, { stateFlow.value = it }, equalityPolicy)
    }

    protected fun <OWNER: BaseViewModel, T, U> binding(
        stateFlow: MutableStateFlow<U>,
        readMapping: (U) -> T,
        writeMapping: (T) -> U,
        equalityPolicy: ViewModelProperty.EqualityPolicy<T>,
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        return BoundPropertyProvider(readMapping(stateFlow.value), stateFlow, readMapping, { stateFlow.value = writeMapping(it) }, equalityPolicy)
    }

    protected fun <OWNER: BaseViewModel, T> binding(
        stateFlow: StateFlow<T>,
        equalityPolicy: ViewModelProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
        set: (T) -> Unit,
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        return BoundPropertyProvider(stateFlow.value, stateFlow, { it }, set, equalityPolicy)
    }

    protected fun <OWNER: BaseViewModel, T, U> binding(
        stateFlow: StateFlow<U>,
        mapping: (U) -> T,
        set: (T) -> Unit,
        equalityPolicy: ViewModelProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        return BoundPropertyProvider(mapping(stateFlow.value), stateFlow, mapping, set, equalityPolicy)
    }

    // MARK:- Locks
    protected val instanceLock: InterfaceLock
        get() = locks.instanceLock

    protected fun createLock(group: InterfaceLock.Group? = null): InterfaceLock = locks.createLock(group)

    protected fun objectLock(obj: Any): InterfaceLock = locks.objectLock(obj)

    protected fun propertyLock(property: KProperty<*>, group: InterfaceLock.Group? = null): InterfaceLock
        = locks.propertyLock(property, group)

    protected fun functionLock(function: KFunction<*>, group: InterfaceLock.Group? = null): InterfaceLock
        = locks.functionLock(function, group)

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

    protected operator fun <OWNER: BaseViewModel, T> ViewModelProperty<T>.provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadOnlyProperty<OWNER, T> {
        registerViewModelProperty(property, this)
        return toKotlinProperty()
    }

    protected operator fun <OWNER: BaseViewModel, T> MutableViewModelProperty<T>.provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, T> {
        registerViewModelProperty(property, this)
        return toKotlinMutableProperty()
    }

    internal fun <T> registerViewModelProperty(property: KProperty<*>, viewModelProperty: ViewModelProperty<T>) {
        check(!properties.containsKey(property.name)) {
            "ViewModelProperty for name ${property.name} (property: $property) already registered!"
        }
        properties[property.name] = viewModelProperty
        viewModelProperty.addListener(object: ViewModelProperty.ValueChangeListener<T> {
            override fun valueWillChange(newValue: T) {
                changeTrackingTrigger.notifyObjectWillChange()
            }

            override fun valueDidChange(oldValue: T) {
                changeTrackingTrigger.notifyObjectDidChange()
            }
        })
    }

    private fun <T, RESULT> withNonRepeatingStateFlow(stateFlow: StateFlow<T>, block: (initialValue: T, autoFilteredFlow: Flow<T>) -> RESULT): RESULT {
        var latestValue: T = stateFlow.value
        return block(latestValue, stateFlow.filter { it != latestValue }.onEach { latestValue = it })
    }

    private inner class LockRegistry {
        val instanceLock by lazy {
            InterfaceLock(lifecycle)
        }
        private val objectLocks = mutableMapOf<Any, InterfaceLock>()
        private val propertyLocks = mutableMapOf<KProperty<*>, MutableMap<InterfaceLock.Group?, InterfaceLock>>()
        private val functionLocks = mutableMapOf<KFunction<*>, MutableMap<InterfaceLock.Group?, InterfaceLock>>()

        fun createLock(group: InterfaceLock.Group? = null) = InterfaceLock(lifecycle, group ?: InterfaceLock.Group())

        fun objectLock(obj: Any): InterfaceLock = objectLocks.getOrPut(obj) {
            InterfaceLock(lifecycle)
        }

        fun propertyLock(property: KProperty<*>, group: InterfaceLock.Group?): InterfaceLock = propertyLocks
            .getOrPut(property) { mutableMapOf() }
            .getOrPut(group) { InterfaceLock(lifecycle, group ?: InterfaceLock.Group()) }


        fun functionLock(function: KFunction<*>, group: InterfaceLock.Group?): InterfaceLock = functionLocks
            .getOrPut(function) { mutableMapOf() }
            .getOrPut(group) { InterfaceLock(lifecycle, group ?: InterfaceLock.Group()) }
    }
}
