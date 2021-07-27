package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.property.ViewModelProperty
import org.brightify.hyperdrive.multiplatformx.property.toKotlinProperty
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal open class ViewModelPropertyProvider<OWNER: BaseViewModel, T>(
    private val viewModelPropertyFactory: (owner: OWNER) -> ViewModelProperty<T>,
): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, T>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadOnlyProperty<OWNER, T> {
        return viewModelPropertyFactory(thisRef)
            .also { thisRef.registerViewModelProperty(property, it) }
            .toKotlinProperty()
    }
}
