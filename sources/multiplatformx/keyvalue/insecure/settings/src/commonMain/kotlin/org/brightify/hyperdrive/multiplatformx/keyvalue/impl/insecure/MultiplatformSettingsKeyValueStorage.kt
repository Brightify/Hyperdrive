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
    override val storageSecurity: KeyValueStorage.StorageSecurity = KeyValueStorage.StorageSecurity.Insecure

    private val json = Json {
        classDiscriminator = "_type"
    }
    private val flowSettings = settings.toFlowSettings()

    override fun <T: Any> contains(key: KeyValueStorage.Key<T>): Boolean = settings.contains(key.key)

    override fun purge(): Unit = settings.clear()

    override fun <T: Any> getSerializable(key: KeyValueStorage.Key<T>): T? {
        val serializableJson = settings.getStringOrNull(key.key) ?: return null
        return json.decodeFromString(key.serializer, serializableJson)
    }

    override fun getBoolean(key: KeyValueStorage.Key<Boolean>): Boolean? = settings.getBooleanOrNull(key.key)
    override fun getDouble(key: KeyValueStorage.Key<Double>): Double? = settings.getDoubleOrNull(key.key)
    override fun getFloat(key: KeyValueStorage.Key<Float>): Float? = settings.getFloatOrNull(key.key)
    override fun getInt(key: KeyValueStorage.Key<Int>): Int? = settings.getIntOrNull(key.key)
    override fun getLong(key: KeyValueStorage.Key<Long>): Long? = settings.getLongOrNull(key.key)
    override fun getString(key: KeyValueStorage.Key<String>): String? = settings.getStringOrNull(key.key)

    override fun <T: Any> observeSerializable(key: KeyValueStorage.Key<T>): Flow<T?> =
        flowSettings.getStringOrNullFlow(key.key).map {
            it?.let { json.decodeFromString(key.serializer, it) }
        }

    override fun observeBoolean(key: KeyValueStorage.Key<Boolean>): Flow<Boolean?> =
        flowSettings.getBooleanOrNullFlow(key.key)

    override fun observeDouble(key: KeyValueStorage.Key<Double>): Flow<Double?> =
        flowSettings.getDoubleOrNullFlow(key.key)

    override fun observeFloat(key: KeyValueStorage.Key<Float>): Flow<Float?> =
        flowSettings.getFloatOrNullFlow(key.key)

    override fun observeInt(key: KeyValueStorage.Key<Int>): Flow<Int?> =
        flowSettings.getIntOrNullFlow(key.key)

    override fun observeLong(key: KeyValueStorage.Key<Long>): Flow<Long?> =
        flowSettings.getLongOrNullFlow(key.key)

    override fun observeString(key: KeyValueStorage.Key<String>): Flow<String?> =
        flowSettings.getStringOrNullFlow(key.key)

    override fun <T: Any> setSerializable(key: KeyValueStorage.Key<T>, value: T?) {
        if (value != null) {
            val serializedJson = json.encodeToString(key.serializer, value)
            settings[key.key] = serializedJson
        } else {
            settings.remove(key.key)
        }
    }

    override fun setBoolean(key: KeyValueStorage.Key<Boolean>, value: Boolean?): Unit = setOrRemove(key, value) {
        settings[key.key] = it
    }

    override fun setDouble(key: KeyValueStorage.Key<Double>, value: Double?): Unit = setOrRemove(key, value) {
        settings[key.key] = it
    }

    override fun setFloat(key: KeyValueStorage.Key<Float>, value: Float?): Unit = setOrRemove(key, value) {
        settings[key.key] = it
    }

    override fun setInt(key: KeyValueStorage.Key<Int>, value: Int?): Unit = setOrRemove(key, value) {
        settings[key.key] = it
    }

    override fun setLong(key: KeyValueStorage.Key<Long>, value: Long?): Unit = setOrRemove(key, value) {
        settings[key.key] = it
    }

    override fun setString(key: KeyValueStorage.Key<String>, value: String?): Unit = setOrRemove(key, value) {
        settings[key.key] = it
    }

    private inline fun <T: Any> setOrRemove(key: KeyValueStorage.Key<T>, value: T?, doSet: (T) -> Unit) {
        if (value != null) {
            doSet(value)
        } else {
            settings.remove(key.key)
        }
    }
}