package org.brightify.hyperdrive.multiplatformx.keyvalue.impl.insecure

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.contains
import com.russhwolf.settings.coroutines.toFlowSettings
import com.russhwolf.settings.set
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.brightify.hyperdrive.multiplatformx.keyvalue.KeyValueStorage
import org.brightify.hyperdrive.multiplatformx.keyvalue.impl.BaseKeyValueStorage

@OptIn(ExperimentalSettingsApi::class, ExperimentalCoroutinesApi::class)
public class MultiplatformSettingsKeyValueStorage(private val settings: ObservableSettings): BaseKeyValueStorage() {

    private val json = Json {
        classDiscriminator = "_type"
    }
    private val flowSettings = settings.toFlowSettings()

    override fun <T: Any> contains(storageKey: KeyValueStorage.StorageKey<T>): Boolean = settings.contains(storageKey.key)

    override fun purge(): Unit = settings.clear()

    override fun <T: Any> getSerializable(storageKey: KeyValueStorage.StorageKey<T>): T? {
        val serializableJson = settings.getStringOrNull(storageKey.key) ?: return null
        return json.decodeFromString(storageKey.serializer, serializableJson)
    }

    override fun getBoolean(storageKey: KeyValueStorage.StorageKey<Boolean>): Boolean? = settings.getBooleanOrNull(storageKey.key)
    override fun getDouble(storageKey: KeyValueStorage.StorageKey<Double>): Double? = settings.getDoubleOrNull(storageKey.key)
    override fun getFloat(storageKey: KeyValueStorage.StorageKey<Float>): Float? = settings.getFloatOrNull(storageKey.key)
    override fun getInt(storageKey: KeyValueStorage.StorageKey<Int>): Int? = settings.getIntOrNull(storageKey.key)
    override fun getLong(storageKey: KeyValueStorage.StorageKey<Long>): Long? = settings.getLongOrNull(storageKey.key)
    override fun getString(storageKey: KeyValueStorage.StorageKey<String>): String? = settings.getStringOrNull(storageKey.key)

    override fun <T: Any> getSerializableFlow(storageKey: KeyValueStorage.StorageKey<T>): Flow<T?> =
        flowSettings.getStringOrNullFlow(storageKey.key).map {
            it?.let { json.decodeFromString(storageKey.serializer, it) }
        }

    override fun getBooleanFlow(storageKey: KeyValueStorage.StorageKey<Boolean>): Flow<Boolean?> =
        flowSettings.getBooleanOrNullFlow(storageKey.key)

    override fun getDoubleFlow(storageKey: KeyValueStorage.StorageKey<Double>): Flow<Double?> =
        flowSettings.getDoubleOrNullFlow(storageKey.key)

    override fun getFloatFlow(storageKey: KeyValueStorage.StorageKey<Float>): Flow<Float?> =
        flowSettings.getFloatOrNullFlow(storageKey.key)

    override fun getIntFlow(storageKey: KeyValueStorage.StorageKey<Int>): Flow<Int?> =
        flowSettings.getIntOrNullFlow(storageKey.key)

    override fun getLongFlow(storageKey: KeyValueStorage.StorageKey<Long>): Flow<Long?> =
        flowSettings.getLongOrNullFlow(storageKey.key)

    override fun getStringFlow(storageKey: KeyValueStorage.StorageKey<String>): Flow<String?> =
        flowSettings.getStringOrNullFlow(storageKey.key)

    override fun <T: Any> setSerializable(storageKey: KeyValueStorage.StorageKey<T>, value: T?) {
        if (value != null) {
            val serializedJson = json.encodeToString(storageKey.serializer, value)
            settings[storageKey.key] = serializedJson
        } else {
            settings.remove(storageKey.key)
        }
    }

    override fun setBoolean(storageKey: KeyValueStorage.StorageKey<Boolean>, value: Boolean?): Unit = setOrRemove(storageKey, value) {
        settings[storageKey.key] = it
    }

    override fun setDouble(storageKey: KeyValueStorage.StorageKey<Double>, value: Double?): Unit = setOrRemove(storageKey, value) {
        settings[storageKey.key] = it
    }

    override fun setFloat(storageKey: KeyValueStorage.StorageKey<Float>, value: Float?): Unit = setOrRemove(storageKey, value) {
        settings[storageKey.key] = it
    }

    override fun setInt(storageKey: KeyValueStorage.StorageKey<Int>, value: Int?): Unit = setOrRemove(storageKey, value) {
        settings[storageKey.key] = it
    }

    override fun setLong(storageKey: KeyValueStorage.StorageKey<Long>, value: Long?): Unit = setOrRemove(storageKey, value) {
        settings[storageKey.key] = it
    }

    override fun setString(storageKey: KeyValueStorage.StorageKey<String>, value: String?): Unit = setOrRemove(storageKey, value) {
        settings[storageKey.key] = it
    }

    private inline fun <T: Any> setOrRemove(storageKey: KeyValueStorage.StorageKey<T>, value: T?, doSet: (T) -> Unit) {
        if (value != null) {
            doSet(value)
        } else {
            settings.remove(storageKey.key)
        }
    }
}