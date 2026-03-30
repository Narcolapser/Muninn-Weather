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
                    unit = obj.optString("unit")
                )
            )
        }
        return items
    }

    data class HaConfig(val url: String, val token: String)

    companion object {
        private const val PREFS_NAME = "muninn_weather_prefs"
        private const val KEY_HA_URL = "ha_url"
        private const val KEY_HA_TOKEN = "ha_token"
        private const val KEY_ENTITY_ID = "entity_id"
        private const val KEY_PACKETS = "weather_packets"
        private const val KEY_POLLING_INTERVAL_MINUTES = "polling_interval_minutes"
        private const val DEFAULT_POLLING_INTERVAL_MINUTES = 15
        private const val MAX_PACKETS = 20
    }
}
