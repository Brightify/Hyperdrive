package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.property.MutableViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.toKotlinMutableProperty
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal open class MutableViewModelPropertyProvider<OWNER: BaseViewModel, T>(
    private val viewModelPropertyFactory: (owner: OWNER) -> MutableViewModelProperty<T>,
): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, T> {
        return viewModelPropertyFactory(thisRef)
            .also { thisRef.registerViewModelProperty(property, it) }
            .toKotlinMutableProperty()
    }
}