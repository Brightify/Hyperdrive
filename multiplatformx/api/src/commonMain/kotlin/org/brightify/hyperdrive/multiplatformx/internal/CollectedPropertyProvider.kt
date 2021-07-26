package org.brightify.hyperdrive.multiplatformx.internal

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.property.impl.CollectedViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.toKotlinProperty
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal class CollectedPropertyProvider<OWNER: BaseViewModel, T>(
    private val initialValue: T,
    private val flow: Flow<T>,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<T>,
): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, T>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadOnlyProperty<OWNER, T> {
        return CollectedViewModelProperty(flow, thisRef.lifecycle, equalityPolicy, initialValue)
            .also { thisRef.registerViewModelProperty(property, it) }
            .toKotlinProperty()
    }
}