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
}
