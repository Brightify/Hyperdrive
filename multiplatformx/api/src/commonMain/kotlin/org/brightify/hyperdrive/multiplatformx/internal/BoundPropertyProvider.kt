package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.flow.MutableStateFlow
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.property.impl.BindingViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.toKotlinMutableProperty
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class BoundPropertyProvider<OWNER: BaseViewModel, T, U>(
    private val stateFlow: MutableStateFlow<U>,
    private val readMapping: (U) -> T,
    private val writeMapping: (T) -> U,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<T>,
): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, T> {
        return BindingViewModelProperty(stateFlow, thisRef.lifecycle, equalityPolicy, readMapping, writeMapping)
            .also { thisRef.registerViewModelProperty(property, it) }
            .toKotlinMutableProperty()
    }
}
