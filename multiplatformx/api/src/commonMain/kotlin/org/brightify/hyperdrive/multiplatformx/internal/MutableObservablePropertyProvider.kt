package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseObservableObject
import org.brightify.hyperdrive.multiplatformx.property.MutableObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.toKotlinMutableProperty
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal open class MutableObservablePropertyProvider<OWNER: BaseObservableObject, T>(
    private val observablePropertyFactory: (owner: OWNER) -> MutableObservableProperty<T>,
): PropertyDelegateProvider<OWNER, ReadWriteProperty<OWNER, T>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadWriteProperty<OWNER, T> {
        return observablePropertyFactory(thisRef)
            .also { thisRef.registerViewModelProperty(property, it) }
            .toKotlinMutableProperty()
    }
}