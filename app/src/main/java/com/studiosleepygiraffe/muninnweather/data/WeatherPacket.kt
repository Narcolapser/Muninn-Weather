package com.studiosleepygiraffe.muninnweather.data

data class WeatherPacket(
    val timestampMillis: Long,
    val temperature: Double,
    val unit: String,
    val source: WeatherSource = WeatherSource.HOME_ASSISTANT,
    val condition: String = "Unknown",
    val conditionCode: Int = 800,
    val locationName: String = "Home Assistant"
)

enum class WeatherSource {
    HOME_ASSISTANT,
    WEATHER_API
}
