package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.property.impl.ValueViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.toKotlinMutableProperty
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class PublishedPropertyProvider<OWNER: BaseViewModel, T>(
    private val initialValue: T,
    private val equalityPolicy: ViewModelProperty.EqualityPolicy<T>,
): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, T> {
        return ValueViewModelProperty(initialValue, equalityPolicy)
            .also { thisRef.registerViewModelProperty(property, it) }
            .toKotlinMutableProperty()
    }
}