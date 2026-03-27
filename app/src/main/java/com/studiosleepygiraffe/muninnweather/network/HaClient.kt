package com.studiosleepygiraffe.muninnweather.network

import com.studiosleepygiraffe.muninnweather.data.WeatherPacket
import com.studiosleepygiraffe.muninnweather.data.WeatherStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class HaClient {
    private val client = OkHttpClient()

    suspend fun fetchPacket(config: WeatherStorage.HaConfig, entityId: String): WeatherPacket? {
        return withContext(Dispatchers.IO) {
            val url = "${config.url}/api/states/$entityId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${config.token}")
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val state = json.optString("state", "").trim()
                if (state.isBlank()) return@withContext null
                val attributes = json.optJSONObject("attributes")
                val unit = attributes?.optString("unit_of_measurement", "") ?: ""

                WeatherPacket(
                    timestampMillis = System.currentTimeMillis(),
                    temperature = state.toDoubleOrNull() ?: return@withContext null,
                    unit = unit
                )
            }
        }
    }

    suspend fun fetchSensors(config: WeatherStorage.HaConfig): List<HaSensor> {
        return withContext(Dispatchers.IO) {
            val url = "${config.url}/api/states"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${config.token}")
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val array = org.json.JSONArray(body)
                val items = mutableListOf<HaSensor>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val entityId = obj.optString("entity_id", "")
                    if (!entityId.startsWith("sensor.")) continue
                    val name = obj.optJSONObject("attributes")
                        ?.optString("friendly_name", entityId)
                        ?.trim()
                        ?.ifBlank { entityId }
                        ?: entityId
                    items.add(HaSensor(entityId, name))
                }
                items
            }
        }
    }

    data class HaSensor(val entityId: String, val name: String)
}
