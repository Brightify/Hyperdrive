package org.brightify.hyperdrive.multiplatformx.keyvalue

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer

public interface KeyValueStorage {

    /**
     * Checks whether the storage contains a value with given key.
     */
    public fun <T: Any> contains(storageKey: StorageKey<T>): Boolean

    // MARK: Getters for given key. Return `null` if not found and default value not set.
    /**
     * To use this method, a deserialization strategy is required.
     * You can simply use global function `serializer()`, but make sure to mark the class you're fetching as `@Serializable`.
     */
    public operator fun <T: Any> get(storageKey: StorageKey<T>): T?
    public fun <T: Any> get(storageKey: StorageKey<T>, defaultValue: T): T

    // MARK: Flow getters for given key. Emit `null` if not found and default value not set.
    public fun <T: Any> getFlow(storageKey: StorageKey<T>): Flow<T?>
    public fun <T: Any> getFlow(storageKey: StorageKey<T>, defaultValue: T): Flow<T>

    // MARK: - Setter methods for given key.
    /**
     * To use this method, a serialization strategy is required.
     * You can simply use global function `serializer()`, but make sure to mark the class you're storing as `@Serializable`.
     */
    public operator fun <T: Any> set(storageKey: StorageKey<T>, value: T?)

    public fun purge()

    public enum class StorageType {
        Secure,
        Insecure,
    }

    public interface StorageKey<T: Any> {

        public val key: String
            get() = this::class.simpleName!!
        public val serializer: KSerializer<T>
    }
}
