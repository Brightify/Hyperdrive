@file:Suppress("MemberVisibilityCanBePrivate")

package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.brightify.hyperdrive.multiplatformx.internal.CollectedPropertyProvider
import org.brightify.hyperdrive.multiplatformx.internal.ManagedPropertyProvider
import org.brightify.hyperdrive.multiplatformx.internal.PublishedListPropertyProvider
import org.brightify.hyperdrive.multiplatformx.internal.PublishedMutableListPropertyProvider
import org.brightify.hyperdrive.multiplatformx.internal.PublishedPropertyProvider
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

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
public abstract class BaseViewModel {
    private val propertyObservers = mutableMapOf<String, MutableStateFlow<*>>()

    private val objectWillChangeTrigger = BroadcastChannel<Unit>(Channel.CONFLATED)
    public val observeObjectWillChange: Flow<Unit> = objectWillChangeTrigger.asFlow()
    @Suppress("unused")
    public val observeObjectWillChangeWrapper: NonNullFlowWrapper<Unit> = NonNullFlowWrapper(observeObjectWillChange)

    public val lifecycle: Lifecycle = Lifecycle()

    init {
        lifecycle.whileAttached {
            whileAttached()
        }
    }

    /**
     * Returns [StateFlow] for the given property.
     *
     * **NOTE**: Although this method can be called manually, it's not recommended due to a risk of runtime crashes. The property passed in
     * is matched by its name, so when called with a property from a different class, but a same name and different type, it will return
     * the stored [StateFlow] and crash later when its [value][StateFlow.value] is accessed or the flow is collected.
     */
    protected fun <T> observe(property: KProperty0<T>): Lazy<StateFlow<T>> = lazy { getPropertyObserver(property, property.get()) }

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
    protected fun <OWNER, T> published(initialValue: T): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        return PublishedPropertyProvider(this, initialValue)
    }

    /**
     * Property delegate used for property mutation tracking for the [MutableList] type.
     *
     * Use with any property that is mutable and its mutation invalidates the view model.
     *
     * **IMPORTANT**: Since [MutableList] in Kotlin is a reference type, this property delegate automatically wraps the stored [MutableList] instance into
     * a proxy that's used to track modifications to the list itself (adding items, removing items etc.). Due to this you need to keep in mind
     * the two following scenarios:
     * 1. Mutating the instance of a [MutableList] set to a property using this delegate will not trigger the [observeObjectWillChange].
     * 2. Mutating an instance of a [MutableList] retrieved from this property will trigger [observeObjectWillChange] even when the property
     *    is set to a new value.
     *
     * Do **NOT** rely on the second behavior as it is a subject to change.
     */
    protected fun <OWNER, T> published(initialValue: MutableList<T>): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, MutableList<T>>> {
        return PublishedMutableListPropertyProvider(this, initialValue)
    }

    /**
     * Property delegate used to mirror an instance of [StateFlow].
     *
     * The property using this delegate will keep its value synchronized with the [StateFlow] as long as the [Lifecycle] of this view model
     * is attached. Although [StateFlow] always has a value that can be accessed in synchronous code, this delegate only uses the value
     * as an initial value. This means that when detached, the property is not kept in sync with the soruce [StateFlow] instance.
     */
    protected fun <OWNER, T> collected(stateFlow: StateFlow<T>): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, T>> {
        return CollectedPropertyProvider(this, stateFlow.value, stateFlow)
    }

    /**
     * Property delegate used to mirror an instance of [StateFlow], mapping its value.
     *
     * @see collected
     */
    protected fun <OWNER, T, U> collected(stateFlow: StateFlow<T>, mapping: (T) -> U): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> {
        return CollectedPropertyProvider(this, mapping(stateFlow.value), stateFlow.map { mapping(it) })
    }

    /**
     * Property delegate used to mirror the latest value of a [Flow].
     *
     * As opposed to the [collected] method for [StateFlow], this one requires passing in an [initialValue] so that this property has a value
     * even before the view model's [lifecycle] gets attached.
     *
     * @see collected
     */
    protected fun <OWNER, T> collected(initialValue: T, flow: Flow<T>): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, T>> {
        return CollectedPropertyProvider(this, initialValue, flow)
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
    protected fun <OWNER, T, U> collected(initialValue: T, flow: Flow<T>, mapping: (T) -> U): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> {
        return CollectedPropertyProvider(this, mapping(initialValue), flow.map { mapping(it) })
    }

    /**
     * Property delegate used for view model composition.
     *
     * Any child view model that is a part of your view model should be initated using the managed delegate. Its [lifecycle] then automatically
     * attached and detached from the parent view model's [lifecycle].
     *
     * @sample org.brightify.hyperdrive.multiplatformx.BaseViewModelSamples.managed
     */
    protected fun <OWNER, T: BaseViewModel?> managed(childModel: T): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
        return ManagedPropertyProvider(this, childModel)
    }

    // TODO: Return a list proxy that will trigger the `objectWillLoad` every time its contents are changed and also that keeps each item managed.
    // protected fun <OWNER, T: BaseInterfaceModel> managed(childModels: List<T>): ReadWriteProperty<OWNER, MutableList<T>>

    /**
     * Informs the object that it's about to change/mutate.
     *
     * This has to be called before any mutation takes place. When using the provided property delegates, there should be little to no need
     * to call this method directly.
     *
     * @sample org.brightify.hyperdrive.multiplatformx.BaseViewModelSamples.notifyObjectWillChange
     */
    protected fun notifyObjectWillChange() {
        objectWillChangeTrigger.offer(Unit)
    }

    internal fun internalNotifyObjectWillChange() {
        objectWillChangeTrigger.offer(Unit)
    }

    internal fun <T> getPropertyObserver(property: KProperty<*>, initialValue: T): MutableStateFlow<T> {
        return propertyObservers.getOrPut(property.name) {
            MutableStateFlow(initialValue)
        } as MutableStateFlow<T>
    }
}
