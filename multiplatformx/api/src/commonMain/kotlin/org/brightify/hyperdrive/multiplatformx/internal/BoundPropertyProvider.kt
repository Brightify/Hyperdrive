package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.property.impl.BindingViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.toKotlinMutableProperty
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class BoundPropertyProvider<OWNER: BaseViewModel, T, U>(
    private val initialValue: T,
    private val flow: Flow<U>,
    private val readMapping: (U) -> T,
    private val setter: (T) -> Unit,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<T>,
): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, T> {
        return BindingViewModelProperty(initialValue, flow, thisRef.lifecycle, equalityPolicy, readMapping, setter)
            .also { thisRef.registerViewModelProperty(property, it) }
            .toKotlinMutableProperty()
    }
}
