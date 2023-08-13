package org.brightify.hyperdrive.keyvalue

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A storage for key-value pairs. Implementations can either be secure or insecure. Secure storage should store data in a secure enclave on
 * the device. Insecure storage can save directly to disk with no need for encryption.
 *
 * An insecure storage can be used for user preferences, application settings, and other inconsequential data. For more important data,
 * like an authentication token, user identifier, the secure storage should be used. The main reason to use secure storage is to keep any
 * third party from gaining access to the data.
 */
public interface KeyValueStorage {

    /**
     * The security of this storage. It might be a good idea to check before storing important information to avoid storing it insecurely.
     */
    public val storageSecurity: StorageSecurity

    /**
     * Checks whether the storage contains a value with the given [key].
     */
    public fun <T: Any> contains(key: Key<T>): Boolean

    /**
     * Gets the value stored for the given [key], or null if no value was stored for the [key].
     */
    public operator fun <T: Any> get(key: Key<T>): T?

    /**
     * Gets the value stored for the given [key], or returns the passed [defaultValue] if no value was stored for the [key].
     * If no value is stored for the [key], the [defaultValue] will NOT be stored in the storage. Use [set] method to store a value.
     */
    public fun <T: Any> get(key: Key<T>, defaultValue: T): T = get(key) ?: defaultValue

    /**
     * Observe changes to the [key]'s value. The current value is emitted immediately, or `null` if no value has been stored for the [key].
     * If a stored value for the [key] is removed, `null` gets emitted.
     */
    public fun <T: Any> observe(key: Key<T>): Flow<T?>

    /**
     * Observe changes to the [key]'s value. The current value is emitted immediately, or the [defaultValue] if no value has been stored
     * for the [key]. If a stored value for the [key] is removed, [defaultValue] gets emitted.
     */
    public fun <T: Any> observe(key: Key<T>, defaultValue: T): Flow<T> = observe(key).map {
        it ?: defaultValue
    }

    /**
     * Stores the passed [value] for the given [key]. If the value is `null`, the key-value pair is removed from the storage.
     */
    public operator fun <T: Any> set(key: Key<T>, value: T?)

    /**
     * Remove all stored key-values from the storage.
     */
    public fun purge()

    /**
     * Security of the storage, can be used as qualified for dependency injection to support having both secure and insecure implementations
     * available for use.
     */
    public enum class StorageSecurity {
        /**
         * An insecure storage can be used for user preferences, application settings, and other inconsequential data.
         */
        Secure,

        /**
         * A secure storage should be used for more important data, like an authentication token, user identifier, etc.
         */
        Insecure,
    }

    /**
     * Storage key is used to retrieve and store data in the storage. Implement this interface for each piece of data you want to store
     * in either secure or insecure storage. The key itself has zero notion of where the data is stored.
     *
     * ## Example
     *
     * ```
     * object AppStartCounterKey: Key<Int> {
     *     override val key = "AppStartCounterKey"
     *     override val serializer = Int.serializer()
     * }
     * ```
     *
     */
    public interface Key<T: Any> {
        /**
         * The key used by the storage implementation to bind the value to it. It should be unique in the scope of the app.
         *
         * The default implementation tries to use the implementation class' simpleName. If your implementation has no name,
         * for example an abstract class, make sure to override this property to avoid runtime crashes.
         */
        public val key: String
            get() = checkNotNull(this::class.simpleName) {
                "Couldn't get simpleName of ${this::class}. Please override the `key` property and return the key explicitly."
            }

        /**
         * Serializer for the value. It's not guaranteed the serializer will be used by the implementation. The implementation can decide,
         * based on the serializer and/or type, to use a faster way of storing the value without serialization.
         */
        public val serializer: KSerializer<T>
    }
}

/**
 * A property delegate for the given [key]. Behaves as using the [KeyValueStorage.get] and [KeyValueStorage.set] methods.
 */
public fun <OWNER, T: Any> KeyValueStorage.property(key: KeyValueStorage.Key<T>): ReadWriteProperty<OWNER, T?> {
    return object: ReadWriteProperty<OWNER, T?> {
        override fun getValue(thisRef: OWNER, property: KProperty<*>): T? {
            return get(key)
        }

        override fun setValue(thisRef: OWNER, property: KProperty<*>, value: T?) {
            set(key, value)
        }
    }
}

/**
 * A property delegate for the given [key]. Behaves as using the [KeyValueStorage.get] and [KeyValueStorage.set] methods.
 */
public fun <OWNER, T: Any> KeyValueStorage.property(key: KeyValueStorage.Key<T>, defaultValue: T): ReadWriteProperty<OWNER, T> {
    return object: ReadWriteProperty<OWNER, T> {
        override fun getValue(thisRef: OWNER, property: KProperty<*>): T {
            return get(key, defaultValue)
        }

        override fun setValue(thisRef: OWNER, property: KProperty<*>, value: T) {
            set(key, value)
        }
    }
}
