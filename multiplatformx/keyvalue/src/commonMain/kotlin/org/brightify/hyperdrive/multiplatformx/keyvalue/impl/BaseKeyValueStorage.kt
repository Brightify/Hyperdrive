package org.brightify.hyperdrive.multiplatformx.keyvalue.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.multiplatformx.keyvalue.KeyValueStorage

public abstract class BaseKeyValueStorage: KeyValueStorage {

    override fun <T: Any> get(storageKey: KeyValueStorage.StorageKey<T>): T? {
        return when (storageKey.serializer) {
            Boolean.serializer() -> getBoolean(storageKey as KeyValueStorage.StorageKey<Boolean>) as T?
            Double.serializer() -> getDouble(storageKey as KeyValueStorage.StorageKey<Double>) as T?
            Float.serializer() -> getFloat(storageKey as KeyValueStorage.StorageKey<Float>) as T?
            Int.serializer() -> getInt(storageKey as KeyValueStorage.StorageKey<Int>) as T?
            Long.serializer() -> getLong(storageKey as KeyValueStorage.StorageKey<Long>) as T?
            String.serializer() -> getString(storageKey as KeyValueStorage.StorageKey<String>) as T?
            else -> getSerializable(storageKey)
        }
    }

    override fun <T: Any> get(storageKey: KeyValueStorage.StorageKey<T>, defaultValue: T): T = get(storageKey) ?: defaultValue

    override fun <T: Any> getFlow(storageKey: KeyValueStorage.StorageKey<T>): Flow<T?> {
        return when (storageKey.serializer) {
            Boolean.serializer() -> getBooleanFlow(storageKey as KeyValueStorage.StorageKey<Boolean>) as Flow<T?>
            Double.serializer() -> getDoubleFlow(storageKey as KeyValueStorage.StorageKey<Double>) as Flow<T?>
            Float.serializer() -> getFloatFlow(storageKey as KeyValueStorage.StorageKey<Float>) as Flow<T?>
            Int.serializer() -> getIntFlow(storageKey as KeyValueStorage.StorageKey<Int>) as Flow<T?>
            Long.serializer() -> getLongFlow(storageKey as KeyValueStorage.StorageKey<Long>) as Flow<T?>
            String.serializer() -> getStringFlow(storageKey as KeyValueStorage.StorageKey<String>) as Flow<T?>
            else -> getSerializableFlow(storageKey)
        }
    }

    override fun <T: Any> getFlow(storageKey: KeyValueStorage.StorageKey<T>, defaultValue: T): Flow<T> = getFlow(storageKey).map {
        it ?: defaultValue
    }

    override fun <T: Any> set(storageKey: KeyValueStorage.StorageKey<T>, value: T?) {
        when (storageKey.serializer) {
            Boolean.serializer() -> setBoolean(storageKey as KeyValueStorage.StorageKey<Boolean>, value as Boolean?)
            Double.serializer() -> setDouble(storageKey as KeyValueStorage.StorageKey<Double>, value as Double?)
            Float.serializer() -> setFloat(storageKey as KeyValueStorage.StorageKey<Float>, value as Float?)
            Int.serializer() -> setInt(storageKey as KeyValueStorage.StorageKey<Int>, value as Int?)
            Long.serializer() -> setLong(storageKey as KeyValueStorage.StorageKey<Long>, value as Long?)
            String.serializer() -> setString(storageKey as KeyValueStorage.StorageKey<String>, value as String?)
            else -> getSerializable(storageKey)
        }
    }

    protected abstract fun <T: Any> getSerializable(storageKey: KeyValueStorage.StorageKey<T>): T?
    protected abstract fun getBoolean(storageKey: KeyValueStorage.StorageKey<Boolean>): Boolean?
    protected abstract fun getDouble(storageKey: KeyValueStorage.StorageKey<Double>): Double?
    protected abstract fun getFloat(storageKey: KeyValueStorage.StorageKey<Float>): Float?
    protected abstract fun getInt(storageKey: KeyValueStorage.StorageKey<Int>): Int?
    protected abstract fun getLong(storageKey: KeyValueStorage.StorageKey<Long>): Long?
    protected abstract fun getString(storageKey: KeyValueStorage.StorageKey<String>): String?

    protected abstract fun <T: Any> getSerializableFlow(storageKey: KeyValueStorage.StorageKey<T>): Flow<T?>
    protected abstract fun getBooleanFlow(storageKey: KeyValueStorage.StorageKey<Boolean>): Flow<Boolean?>
    protected abstract fun getDoubleFlow(storageKey: KeyValueStorage.StorageKey<Double>): Flow<Double?>
    protected abstract fun getFloatFlow(storageKey: KeyValueStorage.StorageKey<Float>): Flow<Float?>
    protected abstract fun getIntFlow(storageKey: KeyValueStorage.StorageKey<Int>): Flow<Int?>
    protected abstract fun getLongFlow(storageKey: KeyValueStorage.StorageKey<Long>): Flow<Long?>
    protected abstract fun getStringFlow(storageKey: KeyValueStorage.StorageKey<String>): Flow<String?>

    protected abstract fun <T: Any> setSerializable(storageKey: KeyValueStorage.StorageKey<T>, value: T?)
    protected abstract fun setBoolean(storageKey: KeyValueStorage.StorageKey<Boolean>, value: Boolean?)
    protected abstract fun setDouble(storageKey: KeyValueStorage.StorageKey<Double>, value: Double?)
    protected abstract fun setFloat(storageKey: KeyValueStorage.StorageKey<Float>, value: Float?)
    protected abstract fun setInt(storageKey: KeyValueStorage.StorageKey<Int>, value: Int?)
    protected abstract fun setLong(storageKey: KeyValueStorage.StorageKey<Long>, value: Long?)
    protected abstract fun setString(storageKey: KeyValueStorage.StorageKey<String>, value: String?)
}