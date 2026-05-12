package com.studiosleepygiraffe.muninnweather.network

import com.studiosleepygiraffe.muninnweather.data.WeatherPacket
import com.studiosleepygiraffe.muninnweather.data.WeatherSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class OpenMeteoClient {
    private val client = OkHttpClient()

    suspend fun fetchCurrent(latitude: Double, longitude: Double, locationName: String): WeatherPacket? {
        return withContext(Dispatchers.IO) {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude" +
                "&longitude=$longitude" +
                "&current=temperature_2m,weather_code" +
                "&temperature_unit=fahrenheit" +
                "&timezone=auto"
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val current = json.optJSONObject("current") ?: return@withContext null
                val currentUnits = json.optJSONObject("current_units")
                val weatherCode = current.optInt("weather_code", 0)
                WeatherPacket(
                    timestampMillis = System.currentTimeMillis(),
                    temperature = current.optDouble("temperature_2m"),
                    unit = currentUnits?.optString("temperature_2m", "°F") ?: "°F",
                    source = WeatherSource.WEATHER_API,
                    condition = describeWeatherCode(weatherCode),
                    conditionCode = toGadgetbridgeConditionCode(weatherCode),
                    locationName = locationName
                )
            }
        }
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        return withContext(Dispatchers.IO) {
            val url = "https://geocoding-api.open-meteo.com/v1/reverse" +
                "?latitude=$latitude" +
                "&longitude=$longitude" +
                "&language=en" +
                "&format=json"
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val result = JSONObject(body)
                    .optJSONArray("results")
                    ?.optJSONObject(0)
                    ?: return@withContext null
                listOfNotNull(
                    result.optString("name").takeIf { it.isNotBlank() },
                    result.optString("admin1").takeIf { it.isNotBlank() },
                    result.optString("country_code").takeIf { it.isNotBlank() }
                ).joinToString(", ")
            }
        }
    }

    suspend fun geocode(query: String): GeocodedLocale? {
        return withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val result = JSONObject(body)
                    .optJSONArray("results")
                    ?.optJSONObject(0)
                    ?: return@withContext null
                GeocodedLocale(
                    name = listOfNotNull(
                        result.optString("name").takeIf { it.isNotBlank() },
                        result.optString("admin1").takeIf { it.isNotBlank() },
                        result.optString("country_code").takeIf { it.isNotBlank() }
                    ).joinToString(", "),
                    latitude = result.optDouble("latitude"),
                    longitude = result.optDouble("longitude")
                )
            }
        }
    }

    private fun describeWeatherCode(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Rain"
        71, 73, 75, 77, 85, 86 -> "Snow"
        95, 96, 99 -> "Thunderstorm"
        else -> "Unknown"
    }

    private fun toGadgetbridgeConditionCode(code: Int): Int = when (code) {
        0 -> 800
        1, 2 -> 801
        3 -> 804
        45, 48 -> 741
        51, 53, 55, 56, 57 -> 300
        61, 63, 65, 66, 67, 80, 81, 82 -> 500
        71, 73, 75, 77, 85, 86 -> 600
        95, 96, 99 -> 200
        else -> 800
    }

    data class GeocodedLocale(val name: String, val latitude: Double, val longitude: Double)
}
