@file:Suppress("MemberVisibilityCanBePrivate")

package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.flow.StateFlow
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty

/**
 * # Common behavior for all view models.
 *
 * This class inherits property delegates for change tracking and observing from [BaseObservableManageableObject] and [BaseObservableObject].
 * As a single point of interaction with the native code for both Android and iOS (and possible other platforms), view models have to
 * provide an easy way for the native code to consume them. Thanks to the behavior described below, developers of the view models can work
 * directly with values without asynchronicity.
 *
 * Declaring properties using the property delegates adds them for mutation tracking through the [changeTracking]. Each change to those
 * properties then triggers the [changeTracking]'s `willChange` and `didChange` events.
 *
 * ## Support for SwiftUI
 *
 * In the native code that depends on a module using [BaseViewModel], add the following implementation:
 *
 * ```
 * #warning("Import the Kotlin multiplatform framework in place of this warning.")
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
 *         willChange.addObserver {
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
 * For Jetpack Compose support make sure to include the `multiplatformx-compose` in your dependencies. Each [BaseViewModel] and its
 * observable properties will be convertible to Compose [State] using [observeAsState()] function.
 *
 * ### With AutoObserve (default)
 *
 * When using the Hyperdrive Gradle plugin, once `multiplatformx` is enabled, each `@Composable` function's IR is modified
 * to automatically observe any view model parameters.
 *
 * ### Without AutoObserve
 *
 * To access an observable property, use the [observe] method, passing it a property which has been declared using one of the provided
 * property delegates. To further minimize required boilerplate, the Hyperdrive Kotlin compiler plugin generates `observeX` properties
 * for each property `x` serving as quick access to the [ObservableProperty] for the given property.
 *
 * ```
 * @ViewModel
 * class SampleViewModel: BaseViewModel() {
 *     var message: String by published("Default message")
 *         private set
 * }
 *
 * @Composable
 * fun SampleView1(viewModel: SampleViewModel) {
 *     val observedViewModel by viewModel.observeAsState()
 *     Text(observedViewModel.message)
 * }
 *
 * @Composable
 * fun SampleView2(viewModel: SampleViewModel) {
 *     val message by viewModel.observeMessage.observeAsState()
 *     Text(message)
 * }
 * ```
 * @see BaseObservableObject for basic change tracking property delegates.
 * @see BaseObservableManageableObject for observing property delegates.
 *
 * **NOTE**: Annotate your class with [ViewModel] and make it inherit [BaseViewModel] to mark the class for processing by the Hyperdrive Kotlin compiler plugin.
 *
 * ## Future plans:
 * - Maybe replace delegates with annotations and make the plugin replace the code during compilation. This would probably result in a better
 *   readability, but more magic behind the scenes.
 * - Include required Swift code as a template in resources allowing for an easy import.
 */
public abstract class BaseViewModel: BaseObservableManageableObject(), ManageableViewModel {
    private val locks = LockRegistry()

    // MARK:- Locks
    protected val instanceLock: InterfaceLock
        get() = locks.instanceLock

    /**
     * Create a new lock. When provided with a group, the lock will compete with other locks linked to this group.
     */
    protected fun createLock(group: InterfaceLock.Group? = null): InterfaceLock = locks.createLock(group)

    /**
     * Get (or create) a lock lock that is specific to the provided object.
     */
    protected fun objectLock(obj: Any): InterfaceLock = locks.objectLock(obj)

    /**
     * Get (or create) a lock that is specific to the provided property with support for providing a lock group.
     */
    protected fun propertyLock(property: KProperty<*>, group: InterfaceLock.Group? = null): InterfaceLock
        = locks.propertyLock(property, group)

    /**
     * Get (or create) a lock that is specific to the provided function with support for providing a lock group.
     */
    protected fun functionLock(function: KFunction<*>, group: InterfaceLock.Group? = null): InterfaceLock
        = locks.functionLock(function, group)


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
