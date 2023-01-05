package org.brightify.hyperdrive.multiplatformx.internal

import org.brightify.hyperdrive.multiplatformx.BaseObservableObject
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.toKotlinProperty
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal open class ObservablePropertyProvider<OWNER: BaseObservableObject, T>(
    private val observablePropertyFactory: (owner: OWNER) -> ObservableProperty<T>,
): PropertyDelegateProvider<OWNER, ReadOnlyProperty<OWNER, T>> {
    override fun provideDelegate(thisRef: OWNER, property: KProperty<*>): ReadOnlyProperty<OWNER, T> {
        return observablePropertyFactory(thisRef)
            .also { thisRef.registerViewModelProperty(property, it) }
            .toKotlinProperty()
    }
}
