package org.brightify.hyperdrive

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.withIndex
import org.brightify.hyperdrive.internal.AsyncBoundPropertyProvider
import org.brightify.hyperdrive.internal.BoundPropertyProvider
import org.brightify.hyperdrive.internal.CollectedPropertyProvider
import org.brightify.hyperdrive.internal.ManagedListPropertyProvider
import org.brightify.hyperdrive.internal.ManagedPropertyProvider
import org.brightify.hyperdrive.internal.MutableManagedListPropertyProvider
import org.brightify.hyperdrive.internal.MutableManagedPropertyProvider
import org.brightify.hyperdrive.internal.ObservablePropertyProvider
import org.brightify.hyperdrive.property.DeferredObservableProperty
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.property.defaultEqualityPolicy
import org.brightify.hyperdrive.property.flatMapLatest
import org.brightify.hyperdrive.property.impl.AsyncMapDeferredObservableProperty
import org.brightify.hyperdrive.property.impl.CollectedObservableProperty
import org.brightify.hyperdrive.property.impl.ValueObservableProperty
import org.brightify.hyperdrive.property.map
import org.brightify.hyperdrive.util.AsyncQueue
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty

@OptIn(ExperimentalCoroutinesApi::class)
public abstract class BaseObservableManageableObject: BaseObservableObject(), ObservableManageableObject {
    public final override val lifecycle: Lifecycle = Lifecycle(this)

    init {
        lifecycle.whileAttached {
            whileAttached()
        }
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
     * Property delegate used to mirror an instance of [StateFlow].
     *
     * The property using this delegate will keep its value synchronized with the [StateFlow] as long as the [Lifecycle] of this view model
     * is attached. Although [StateFlow] always has a value that can be accessed in synchronous code, this delegate only uses the value
     * as an initial value. This means that when detached, the property is not kept in sync with the source [StateFlow] instance.
     */
    protected fun <OWNER: BaseObservableManageableObject, T> collected(
        stateFlow: StateFlow<T>,
        equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, T>> =
        withNonRepeatingStateFlow(stateFlow) { initialValue, autoFilteredFlow ->
            CollectedPropertyProvider(initialValue, autoFilteredFlow, equalityPolicy)
        }

    /**
     * Property delegate used to mirror an instance of [StateFlow], mapping its value.
     *
     * @see collected
     */
    protected fun <OWNER: BaseObservableManageableObject, T, U> collected(
        stateFlow: StateFlow<T>,
        equalityPolicy: ObservableProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
        mapping: (T) -> U,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> =
        withNonRepeatingStateFlow(stateFlow) { initialValue, autoFilteredFlow ->
            CollectedPropertyProvider(mapping(initialValue), autoFilteredFlow.map { mapping(it) }, equalityPolicy)
        }

    /**
     * Property delegate used to mirror the latest value of a [Flow].
     *
     * As opposed to the [collected] method for [StateFlow], this one requires passing in an [initialValue] so that this property has a value
     * even before the view model's [lifecycle] gets attached.
     *
     * @see collected
     */
    protected fun <OWNER: BaseObservableManageableObject, T> collected(
        initialValue: T,
        flow: Flow<T>,
        equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, T>> = CollectedPropertyProvider(initialValue, flow, equalityPolicy)

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
    protected fun <OWNER: BaseObservableManageableObject, T, U> collected(
        initialValue: T,
        flow: Flow<T>,
        equalityPolicy: ObservableProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
        mapping: (T) -> U,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> =
        CollectedPropertyProvider(mapping(initialValue), flow.map { mapping(it) }, equalityPolicy)

    @Deprecated(message = "This behaves as FlatMapLatest, do not use.", replaceWith = ReplaceWith("collectedFlatMapLatest"))
    protected fun <OWNER: BaseObservableManageableObject, T, U> collectedFlatMap(
        stateFlow: StateFlow<T>,
        equalityPolicy: ObservableProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
        flatMapping: (T) -> StateFlow<U>,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> = collectedFlatMapLatest(stateFlow, equalityPolicy, flatMapping)

    /**
     * Property delegate used to mirror an instance of [StateFlow], flat-mapping its value and only observing the latest's StateFlow's changes.
     *
     * @see collected
     */
    protected fun <OWNER: BaseObservableManageableObject, T, U> collectedFlatMapLatest(
        stateFlow: StateFlow<T>,
        equalityPolicy: ObservableProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
        flatMapping: (T) -> StateFlow<U>,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> = CollectedPropertyProvider(
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

    @Deprecated(message = "This behaves as FlatMapLatest, do not use.", replaceWith = ReplaceWith("collectedFlatMapLatest"))
    protected fun <OWNER: BaseObservableManageableObject, T, U> collectedFlatMap(
        property: ObservableProperty<T>,
        equalityPolicy: ObservableProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
        flatMapping: (T) -> StateFlow<U>,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> = collectedFlatMapLatest(property, equalityPolicy, flatMapping)

    /**
     * Property delegate used to mirror an instance of [ObservableProperty], flat-mapping its value and only observing the latest's [ObservableProperty]'s changes.
     *
     * @see collected
     */
    protected fun <OWNER: BaseObservableManageableObject, T, U> collectedFlatMapLatest(
        property: ObservableProperty<T>,
        equalityPolicy: ObservableProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
        flatMapping: (T) -> StateFlow<U>,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, U>> = ObservablePropertyProvider { owner ->
        property.flatMapLatest { value ->
            withNonRepeatingStateFlow(flatMapping(value)) { initialValue, autoFilteredFlow ->
                CollectedObservableProperty(autoFilteredFlow, owner.lifecycle, equalityPolicy, initialValue)
            }
        }
    }

    /**
     * Property delegate used for view model composition.
     *
     * Any child view model that is a part of your view model should be initiated using the managed delegate. Its [lifecycle] then automatically
     * attached and detached from the parent view model's [lifecycle].
     *
     * @sample org.brightify.hyperdrive.BaseViewModelSamples.managedTest
     */
    protected fun <OWNER: BaseObservableManageableObject, VM: ManageableViewModel?> managed(
        childModel: VM,
        published: Boolean = false,
        equalityPolicy: ObservableProperty.EqualityPolicy<VM> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, VM>> = MutableManagedPropertyProvider(published) {
        ValueObservableProperty(childModel, equalityPolicy)
    }

    /**
     * Property delegate used for view model composition.
     *
     * This variant takes in a [StateFlow] instead of just the value.
     */
    protected fun <OWNER: BaseObservableManageableObject, VM: ManageableViewModel?> managed(
        childStateFlow: StateFlow<VM>,
        published: Boolean = false,
        equalityPolicy: ObservableProperty.EqualityPolicy<VM> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, VM>> =
        withNonRepeatingStateFlow(childStateFlow) { initialValue, autoFilteredFlow ->
            ManagedPropertyProvider(published) { owner ->
                CollectedObservableProperty(autoFilteredFlow, owner.lifecycle, equalityPolicy, initialValue)
            }
        }

    /**
     * Property delegate used for view model composition.
     *
     * This variant takes in a value [StateFlow] and a mapping function that converts it into a view model.
     */
    protected fun <OWNER: BaseObservableManageableObject, T, VM: ManageableViewModel?> managed(
        valueStateFlow: StateFlow<T>,
        published: Boolean = false,
        equalityPolicy: ObservableProperty.EqualityPolicy<VM> = defaultEqualityPolicy(),
        mapping: (T) -> VM,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, VM>> =
        withNonRepeatingStateFlow(valueStateFlow) { initialValue, autoFilteredFlow ->
            ManagedPropertyProvider(published) { owner ->
                CollectedObservableProperty(autoFilteredFlow.map { mapping(it) }, owner.lifecycle, equalityPolicy, mapping(initialValue))
            }
        }

    /**
     * Property delegate used for view model composition.
     *
     * This variant takes in an [ObservableProperty] and a mapping function that converts it into a view model.
     */
    protected fun <OWNER: BaseObservableManageableObject, T, VM: ManageableViewModel?> managed(
        property: ObservableProperty<T>,
        published: Boolean = false,
        equalityPolicy: ObservableProperty.EqualityPolicy<VM> = defaultEqualityPolicy(),
        mapping: (T) -> VM,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, VM>> = ManagedPropertyProvider(published) {
        property.map(equalityPolicy, mapping)
    }

    /**
     * Property delegate used for view model composition.
     *
     * This variant takes in an [ObservableProperty].
     */
    protected fun <OWNER: BaseObservableManageableObject, VM: ManageableViewModel?> managed(
        property: ObservableProperty<VM>,
        published: Boolean = false,
        equalityPolicy: ObservableProperty.EqualityPolicy<VM> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, VM>> = managed(property, published, equalityPolicy) { it }

    /**
     * Property delegate used for view model composition.
     *
     * This variant accepts a [Flow], a mapping closure, but needs an initial view model.
     */
    protected fun <OWNER: BaseObservableManageableObject, T, VM: ManageableViewModel?> managed(
        initialChild: VM,
        valueFlow: Flow<T>,
        published: Boolean = false,
        equalityPolicy: ObservableProperty.EqualityPolicy<VM> = defaultEqualityPolicy(),
        mapping: suspend (T) -> VM,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, VM>> = ManagedPropertyProvider(published) { owner ->
        CollectedObservableProperty(valueFlow.map { mapping(it) }, owner.lifecycle, equalityPolicy, initialChild)
    }

    /**
     * Property delegate used for view model composition.
     *
     * This variant accepts a [Flow], but needs an initial view model.
     */
    protected fun <OWNER: BaseObservableManageableObject, VM: ManageableViewModel?> managed(
        initialChild: VM,
        childFlow: Flow<VM>,
        published: Boolean = false,
        equalityPolicy: ObservableProperty.EqualityPolicy<VM> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, VM>> = ManagedPropertyProvider(published) { owner ->
        CollectedObservableProperty(childFlow, owner.lifecycle, equalityPolicy, initialChild)
    }

    /**
     * List variant property delegate used for view model composition.
     *
     * @sample org.brightify.hyperdrive.BaseViewModelSamples.managedListTest
     */
    protected fun <OWNER: BaseObservableManageableObject, VM: ManageableViewModel?> managedList(
        childModels: List<VM>,
        published: Boolean = false,
        equalityPolicy: ObservableProperty.EqualityPolicy<List<VM>> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, List<VM>>> = MutableManagedListPropertyProvider(published) {
        ValueObservableProperty(childModels, equalityPolicy)
    }

    /**
     * List variant property delegate used for view model composition.
     *
     * @sample org.brightify.hyperdrive.BaseViewModelSamples.managedListTest
     */
    protected fun <OWNER: BaseObservableManageableObject, VM: ManageableViewModel?> managedList(
        childStateFlow: StateFlow<List<VM>>,
        published: Boolean = false,
        equalityPolicy: ObservableProperty.EqualityPolicy<List<VM>> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, List<VM>>> =
        withNonRepeatingStateFlow(childStateFlow) { initialValue, autoFilteredFlow ->
            ManagedListPropertyProvider(published) { owner ->
                CollectedObservableProperty(autoFilteredFlow, owner.lifecycle, equalityPolicy, initialValue)
            }
        }

    /**
     * List variant property delegate used for view model composition.
     *
     * @sample org.brightify.hyperdrive.BaseViewModelSamples.managedListTest
     */
    protected fun <OWNER: BaseObservableManageableObject, T, VM: ManageableViewModel?> managedList(
        valueStateFlow: StateFlow<T>,
        published: Boolean = false,
        equalityPolicy: ObservableProperty.EqualityPolicy<List<VM>> = defaultEqualityPolicy(),
        mapping: (T) -> List<VM>,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, List<VM>>> =
        withNonRepeatingStateFlow(valueStateFlow) { initialValue, autoFilteredFlow ->
            ManagedListPropertyProvider(published) { owner ->
                CollectedObservableProperty(autoFilteredFlow.map { mapping(it) }, owner.lifecycle, equalityPolicy, mapping(initialValue))
            }
        }

    /**
     * List variant property delegate used for view model composition.
     *
     * @sample org.brightify.hyperdrive.BaseViewModelSamples.managedListTest
     */
    protected fun <OWNER: BaseObservableManageableObject, VM: ManageableViewModel?> managedList(
        property: ObservableProperty<List<VM>>,
        published: Boolean = false,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, List<VM>>> = ManagedListPropertyProvider(published) { _ ->
        property
    }

    /**
     * List variant property delegate used for view model composition.
     *
     * This variant accepts a [Flow], a mapping closure, but needs an initial value.
     */
    protected fun <OWNER: BaseObservableManageableObject, T, VM: ManageableViewModel?> managedList(
        initialChild: List<VM>,
        valueFlow: Flow<T>,
        published: Boolean = false,
        equalityPolicy: ObservableProperty.EqualityPolicy<List<VM>> = defaultEqualityPolicy(),
        mapping: suspend (T) -> List<VM>,
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, List<VM>>> = ManagedListPropertyProvider(published) { owner ->
        CollectedObservableProperty(valueFlow.map { mapping(it) }, owner.lifecycle, equalityPolicy, initialChild)
    }

    /**
     * List variant property delegate used for view model composition.
     *
     * This variant accepts a [Flow], but needs an initial value.
     */
    protected fun <OWNER: BaseObservableManageableObject, VM: ManageableViewModel?> managedList(
        initialChild: List<VM>,
        childFlow: Flow<List<VM>>,
        published: Boolean = false,
        equalityPolicy: ObservableProperty.EqualityPolicy<List<VM>> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, List<VM>>> = ManagedListPropertyProvider(published) { owner ->
        CollectedObservableProperty(childFlow, owner.lifecycle, equalityPolicy, initialChild)
    }

    /**
     * Property delegate used to create a two-way binding.
     * Assigning a value to this delegate also modifies the provided [MutableStateFlow].
     */
    protected fun <OWNER: BaseObservableManageableObject, T> binding(
        stateFlow: MutableStateFlow<T>,
        equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> =
        BoundPropertyProvider(stateFlow.value, stateFlow, { it }, { stateFlow.value = it }, equalityPolicy)

    /**
     * Property delegate used to create a two-way binding.
     * This variation allows for mapping the value.
     */
    protected fun <OWNER: BaseObservableManageableObject, T, U> binding(
        stateFlow: MutableStateFlow<U>,
        readMapping: (U) -> T,
        writeMapping: (T) -> U,
        equalityPolicy: ObservableProperty.EqualityPolicy<T>,
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> =
        BoundPropertyProvider(readMapping(stateFlow.value), stateFlow, readMapping, { stateFlow.value = writeMapping(it) }, equalityPolicy)

    /**
     * Property delegate used to create a two-way binding.
     * This variation doesn't set the assigned value back to the [StateFlow] and instead uses the provided setter.
     */
    protected fun <OWNER: BaseObservableManageableObject, T> binding(
        stateFlow: StateFlow<T>,
        equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
        set: (T) -> Unit,
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> =
        BoundPropertyProvider(stateFlow.value, stateFlow, { it }, set, equalityPolicy)

    /**
     * Property delegate used to create a two-way binding.
     * This variation allows for mapping the value
     * and doesn't set the assigned value back to the [StateFlow] and instead uses the provided setter.
     */
    protected fun <OWNER: BaseObservableManageableObject, T, U> binding(
        stateFlow: StateFlow<U>,
        mapping: (U) -> T,
        set: (T) -> Unit,
        equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> =
        BoundPropertyProvider(mapping(stateFlow.value), stateFlow, mapping, set, equalityPolicy)

    /**
     * Property delegate used to create a two-way binding.
     * This variation allows providing an asynchronous setter.
     */
    protected fun <OWNER: BaseObservableManageableObject, T> binding(
        stateFlow: StateFlow<T>,
        equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
        overflowPolicy: AsyncQueue.OverflowPolicy = AsyncQueue.OverflowPolicy.Conflate,
        asyncSet: suspend (T) -> Unit,
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> =
        AsyncBoundPropertyProvider(stateFlow.value, stateFlow, { it }, asyncSet, equalityPolicy, overflowPolicy)

    /**
     * Property delegate used to create a two-way binding.
     * This variation allows for mapping the value and uses the provided asynchronous setter.
     */
    protected fun <OWNER: BaseObservableManageableObject, T, U> binding(
        stateFlow: StateFlow<U>,
        mapping: (U) -> T,
        asyncSet: suspend (T) -> Unit,
        equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
        overflowPolicy: AsyncQueue.OverflowPolicy = AsyncQueue.OverflowPolicy.Conflate,
    ): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> =
        AsyncBoundPropertyProvider(mapping(stateFlow.value), stateFlow, mapping, asyncSet, equalityPolicy, overflowPolicy)

    /**
     * Conversion method from [StateFlow] to [ObservableProperty].
     */
    protected fun <T> StateFlow<T>.asObservable(
        equalityPolicy: ObservableProperty.EqualityPolicy<T> = defaultEqualityPolicy(),
    ): ObservableProperty<T> {
        return CollectedObservableProperty(this, lifecycle, equalityPolicy, value)
    }

    /**
     * Mapping method that uses the provided asynchronous mapping closure.
     */
    protected fun <T, U> ObservableProperty<T>.asyncMap(
        equalityPolicy: ObservableProperty.EqualityPolicy<U> = defaultEqualityPolicy(),
        overflowPolicy: AsyncQueue.OverflowPolicy = AsyncQueue.OverflowPolicy.Conflate,
        block: suspend (T) -> U,
    ): DeferredObservableProperty<U> {
        return AsyncMapDeferredObservableProperty(this, block, lifecycle, equalityPolicy, overflowPolicy)
    }

    private fun <T, RESULT> withNonRepeatingStateFlow(
        stateFlow: StateFlow<T>,
        block: (initialValue: T, autoFilteredFlow: Flow<T>) -> RESULT
    ): RESULT {
        var latestValue: T = stateFlow.value
        return block(latestValue, stateFlow.filter { it != latestValue }.onEach { latestValue = it })
    }
}
