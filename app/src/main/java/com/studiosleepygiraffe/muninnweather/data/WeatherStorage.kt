package com.studiosleepygiraffe.muninnweather.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

class WeatherStorage(context: Context) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasConfig(): Boolean = prefs.contains(KEY_HA_URL) && prefs.contains(KEY_HA_TOKEN)

    fun hasFullConfig(): Boolean = hasConfig() && prefs.contains(KEY_ENTITY_ID)

    fun saveConfig(url: String, token: String) {
        prefs.edit()
            .putString(KEY_HA_URL, url)
            .putString(KEY_HA_TOKEN, token)
            .apply()
    }

    fun getConfig(): HaConfig? {
        val url = prefs.getString(KEY_HA_URL, null)
        val token = prefs.getString(KEY_HA_TOKEN, null)
        if (url.isNullOrBlank() || token.isNullOrBlank()) return null
        return HaConfig(url, token)
    }

    fun saveEntityId(entityId: String) {
        prefs.edit()
            .putString(KEY_ENTITY_ID, entityId)
            .apply()
    }

    fun getEntityId(): String? = prefs.getString(KEY_ENTITY_ID, null)

    fun savePollingIntervalMinutes(minutes: Int) {
        prefs.edit()
            .putInt(KEY_POLLING_INTERVAL_MINUTES, minutes)
            .apply()
    }

    fun getPollingIntervalMinutes(): Int =
        prefs.getInt(KEY_POLLING_INTERVAL_MINUTES, DEFAULT_POLLING_INTERVAL_MINUTES)

    fun saveHomeLocale(homeLocale: HomeLocale) {
        prefs.edit()
            .putString(KEY_HOME_LOCALE_NAME, homeLocale.name)
            .putFloat(KEY_HOME_LATITUDE, homeLocale.latitude.toFloat())
            .putFloat(KEY_HOME_LONGITUDE, homeLocale.longitude.toFloat())
            .apply()
    }

    fun getHomeLocale(): HomeLocale? {
        val name = prefs.getString(KEY_HOME_LOCALE_NAME, null)
        if (name.isNullOrBlank() || !prefs.contains(KEY_HOME_LATITUDE) || !prefs.contains(KEY_HOME_LONGITUDE)) {
            return null
        }
        return HomeLocale(
            name = name,
            latitude = prefs.getFloat(KEY_HOME_LATITUDE, 0f).toDouble(),
            longitude = prefs.getFloat(KEY_HOME_LONGITUDE, 0f).toDouble()
        )
    }

    fun saveCurrentLocale(currentLocale: CurrentLocale) {
        prefs.edit()
            .putString(KEY_CURRENT_LOCALE_NAME, currentLocale.name)
            .putFloat(KEY_CURRENT_LATITUDE, currentLocale.latitude.toFloat())
            .putFloat(KEY_CURRENT_LONGITUDE, currentLocale.longitude.toFloat())
            .putLong(KEY_CURRENT_LOCALE_TIMESTAMP, currentLocale.timestampMillis)
            .apply()
    }

    fun getCurrentLocale(): CurrentLocale? {
        val name = prefs.getString(KEY_CURRENT_LOCALE_NAME, null)
        if (name.isNullOrBlank() || !prefs.contains(KEY_CURRENT_LATITUDE) || !prefs.contains(KEY_CURRENT_LONGITUDE)) {
            return null
        }
        return CurrentLocale(
            name = name,
            latitude = prefs.getFloat(KEY_CURRENT_LATITUDE, 0f).toDouble(),
            longitude = prefs.getFloat(KEY_CURRENT_LONGITUDE, 0f).toDouble(),
            timestampMillis = prefs.getLong(KEY_CURRENT_LOCALE_TIMESTAMP, 0L)
        )
    }

    fun appendPacket(packet: WeatherPacket) {
        val packets = getPackets().toMutableList()
        packets.add(0, packet)
        val trimmed = packets.take(MAX_PACKETS)
        val array = JSONArray()
        for (item in trimmed) {
            val json = JSONObject()
            json.put("timestampMillis", item.timestampMillis)
            json.put("temperature", item.temperature)
            json.put("unit", item.unit)
            json.put("source", item.source.name)
            json.put("condition", item.condition)
            json.put("conditionCode", item.conditionCode)
            json.put("locationName", item.locationName)
            array.put(json)
        }
        prefs.edit().putString(KEY_PACKETS, array.toString()).apply()
    }

    fun getPackets(): List<WeatherPacket> {
        val raw = prefs.getString(KEY_PACKETS, null) ?: return emptyList()
        val array = JSONArray(raw)
        val items = mutableListOf<WeatherPacket>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            items.add(
                WeatherPacket(
                    timestampMillis = obj.optLong("timestampMillis"),
                    temperature = obj.optDouble("temperature"),
                    unit = obj.optString("unit"),
                    source = runCatching {
                        WeatherSource.valueOf(obj.optString("source", WeatherSource.HOME_ASSISTANT.name))
                    }.getOrDefault(WeatherSource.HOME_ASSISTANT),
                    condition = obj.optString("condition", "Unknown"),
                    conditionCode = obj.optInt("conditionCode", 800),
                    locationName = obj.optString("locationName", "Home Assistant")
                )
            )
        }
        return items
    }

    data class HaConfig(val url: String, val token: String)
    data class HomeLocale(val name: String, val latitude: Double, val longitude: Double)
    data class CurrentLocale(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val timestampMillis: Long
    )

    companion object {
        private const val PREFS_NAME = "muninn_weather_prefs"
        private const val KEY_HA_URL = "ha_url"
        private const val KEY_HA_TOKEN = "ha_token"
        private const val KEY_ENTITY_ID = "entity_id"
        private const val KEY_PACKETS = "weather_packets"
        private const val KEY_POLLING_INTERVAL_MINUTES = "polling_interval_minutes"
        private const val KEY_HOME_LOCALE_NAME = "home_locale_name"
        private const val KEY_HOME_LATITUDE = "home_latitude"
        private const val KEY_HOME_LONGITUDE = "home_longitude"
        private const val KEY_CURRENT_LOCALE_NAME = "current_locale_name"
        private const val KEY_CURRENT_LATITUDE = "current_latitude"
        private const val KEY_CURRENT_LONGITUDE = "current_longitude"
        private const val KEY_CURRENT_LOCALE_TIMESTAMP = "current_locale_timestamp"
        private const val DEFAULT_POLLING_INTERVAL_MINUTES = 15
        private const val MAX_PACKETS = 20
    }
}
