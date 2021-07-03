package org.brightify.hyperdrive.viewmodel

object NamingHelper {
    const val OBSERVING_PROPERTY_PREFIX = "observe"

    fun getObservingPropertyName(propertyName: String): String {
        return OBSERVING_PROPERTY_PREFIX + propertyName.replaceFirstChar {
            it.uppercase()
        }
    }

    fun getReferencedPropertyName(observingPropertyName: String): String? {
        return if (observingPropertyName.startsWith(OBSERVING_PROPERTY_PREFIX) && observingPropertyName.count() > OBSERVING_PROPERTY_PREFIX.count()) {
            observingPropertyName.drop(OBSERVING_PROPERTY_PREFIX.count()).replaceFirstChar {
                it.lowercase()
            }
        } else {
            null
        }
    }
}