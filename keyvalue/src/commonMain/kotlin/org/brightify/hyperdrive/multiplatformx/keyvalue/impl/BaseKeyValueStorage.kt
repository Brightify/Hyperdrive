package org.brightify.hyperdrive.keyvalue.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.keyvalue.KeyValueStorage

public abstract class BaseKeyValueStorage: KeyValueStorage {

    override fun <T: Any> get(key: KeyValueStorage.Key<T>): T? {
        return when (key.serializer) {
            Boolean.serializer() -> getBoolean(key as KeyValueStorage.Key<Boolean>) as T?
            Double.serializer() -> getDouble(key as KeyValueStorage.Key<Double>) as T?
            Float.serializer() -> getFloat(key as KeyValueStorage.Key<Float>) as T?
            Int.serializer() -> getInt(key as KeyValueStorage.Key<Int>) as T?
            Long.serializer() -> getLong(key as KeyValueStorage.Key<Long>) as T?
            String.serializer() -> getString(key as KeyValueStorage.Key<String>) as T?
            else -> getSerializable(key)
        }
    }

    override fun <T: Any> observe(key: KeyValueStorage.Key<T>): Flow<T?> {
        return when (key.serializer) {
            Boolean.serializer() -> observeBoolean(key as KeyValueStorage.Key<Boolean>) as Flow<T?>
            Double.serializer() -> observeDouble(key as KeyValueStorage.Key<Double>) as Flow<T?>
            Float.serializer() -> observeFloat(key as KeyValueStorage.Key<Float>) as Flow<T?>
            Int.serializer() -> observeInt(key as KeyValueStorage.Key<Int>) as Flow<T?>
            Long.serializer() -> observeLong(key as KeyValueStorage.Key<Long>) as Flow<T?>
            String.serializer() -> observeString(key as KeyValueStorage.Key<String>) as Flow<T?>
            else -> observeSerializable(key)
        }
    }

    override fun <T: Any> set(key: KeyValueStorage.Key<T>, value: T?) {
        when (key.serializer) {
            Boolean.serializer() -> setBoolean(key as KeyValueStorage.Key<Boolean>, value as Boolean?)
            Double.serializer() -> setDouble(key as KeyValueStorage.Key<Double>, value as Double?)
            Float.serializer() -> setFloat(key as KeyValueStorage.Key<Float>, value as Float?)
            Int.serializer() -> setInt(key as KeyValueStorage.Key<Int>, value as Int?)
            Long.serializer() -> setLong(key as KeyValueStorage.Key<Long>, value as Long?)
            String.serializer() -> setString(key as KeyValueStorage.Key<String>, value as String?)
            else -> setSerializable(key, value)
        }
    }

    protected abstract fun <T: Any> getSerializable(key: KeyValueStorage.Key<T>): T?
    protected abstract fun getBoolean(key: KeyValueStorage.Key<Boolean>): Boolean?
    protected abstract fun getDouble(key: KeyValueStorage.Key<Double>): Double?
    protected abstract fun getFloat(key: KeyValueStorage.Key<Float>): Float?
    protected abstract fun getInt(key: KeyValueStorage.Key<Int>): Int?
    protected abstract fun getLong(key: KeyValueStorage.Key<Long>): Long?
    protected abstract fun getString(key: KeyValueStorage.Key<String>): String?

    protected abstract fun <T: Any> observeSerializable(key: KeyValueStorage.Key<T>): Flow<T?>
    protected abstract fun observeBoolean(key: KeyValueStorage.Key<Boolean>): Flow<Boolean?>
    protected abstract fun observeDouble(key: KeyValueStorage.Key<Double>): Flow<Double?>
    protected abstract fun observeFloat(key: KeyValueStorage.Key<Float>): Flow<Float?>
    protected abstract fun observeInt(key: KeyValueStorage.Key<Int>): Flow<Int?>
    protected abstract fun observeLong(key: KeyValueStorage.Key<Long>): Flow<Long?>
    protected abstract fun observeString(key: KeyValueStorage.Key<String>): Flow<String?>

    protected abstract fun <T: Any> setSerializable(key: KeyValueStorage.Key<T>, value: T?)
    protected abstract fun setBoolean(key: KeyValueStorage.Key<Boolean>, value: Boolean?)
    protected abstract fun setDouble(key: KeyValueStorage.Key<Double>, value: Double?)
    protected abstract fun setFloat(key: KeyValueStorage.Key<Float>, value: Float?)
    protected abstract fun setInt(key: KeyValueStorage.Key<Int>, value: Int?)
    protected abstract fun setLong(key: KeyValueStorage.Key<Long>, value: Long?)
    protected abstract fun setString(key: KeyValueStorage.Key<String>, value: String?)
}
